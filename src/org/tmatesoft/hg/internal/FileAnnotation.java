/*
 * Copyright (c) 2013 TMate Software Ltd
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@hg4j.com
 */
package org.tmatesoft.hg.internal;


import org.tmatesoft.hg.core.HgIterateDirection;
import org.tmatesoft.hg.repo.HgBlameFacility;
import org.tmatesoft.hg.repo.HgInvalidStateException;
import org.tmatesoft.hg.repo.HgBlameFacility.AddBlock;
import org.tmatesoft.hg.repo.HgBlameFacility.BlockData;
import org.tmatesoft.hg.repo.HgBlameFacility.ChangeBlock;
import org.tmatesoft.hg.repo.HgBlameFacility.DeleteBlock;
import org.tmatesoft.hg.repo.HgBlameFacility.EqualBlock;
import org.tmatesoft.hg.repo.HgBlameFacility.RevisionDescriptor;
import org.tmatesoft.hg.repo.HgDataFile;

/**
 * Produce output like 'hg annotate' does
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class FileAnnotation implements HgBlameFacility.BlockInspector, RevisionDescriptor.Recipient {

	@Experimental(reason="The line-by-line inspector likely to become part of core/command API")
	@Callback
	public interface LineInspector {
		/**
		 * Not necessarily invoked sequentially by line numbers
		 */
		void line(int lineNumber, int changesetRevIndex, BlockData lineContent, LineDescriptor ld);
	}

	public interface LineDescriptor {
		int totalLines();
	}

	/**
	 * Annotate file revision, line by line.
	 */
	public static void annotate(HgDataFile df, int changelogRevisionIndex, LineInspector insp) {
		if (!df.exists()) {
			return;
		}
		FileAnnotation fa = new FileAnnotation(insp);
		HgBlameFacility af = new HgBlameFacility();
		af.annotate(df, changelogRevisionIndex, fa, HgIterateDirection.NewToOld);
	}

	// keeps <startSeq1, startSeq2, len> of equal blocks, origin to target, from some previous step
	private RangeSeq activeEquals;
	// equal blocks of the current iteration, to be recalculated before next step
	// to track line number (current target to ultimate target) mapping 
	private RangeSeq intermediateEquals = new RangeSeq();

	private boolean[] knownLines;
	private final LineInspector delegate;
	private RevisionDescriptor revisionDescriptor;
	private BlockData lineContent;

	private IntMap<RangeSeq> mergedRanges = new IntMap<RangeSeq>(10);
	private IntMap<RangeSeq> equalRanges = new IntMap<RangeSeq>(10);
	private boolean activeEqualsComesFromMerge = false;

	public FileAnnotation(LineInspector lineInspector) {
		delegate = lineInspector;
	}

	public void start(RevisionDescriptor rd) {
		revisionDescriptor = rd;
		if (knownLines == null) {
			lineContent = rd.target();
			knownLines = new boolean[lineContent.elementCount()];
			activeEquals = new RangeSeq();
			activeEquals.add(0, 0, knownLines.length);
			equalRanges.put(rd.targetChangesetIndex(), activeEquals);
		} else {
			activeEquals = equalRanges.get(rd.targetChangesetIndex());
			if (activeEquals == null) {
				// we didn't see this target revision as origin yet
				// the only way this may happen is that this revision was a merge parent
				activeEquals = mergedRanges.get(rd.targetChangesetIndex());
				activeEqualsComesFromMerge = true;
				if (activeEquals == null) {
					throw new HgInvalidStateException(String.format("Can't find previously visited revision %d (while in %d->%1$d diff)", rd.targetChangesetIndex(), rd.originChangesetIndex()));
				}
			}
		}
	}

	public void done(RevisionDescriptor rd) {
		// update line numbers of the intermediate target to point to ultimate target's line numbers
		RangeSeq v = intermediateEquals.intersect(activeEquals);
		if (activeEqualsComesFromMerge) {
			mergedRanges.put(rd.originChangesetIndex(), v);
		} else {
			equalRanges.put(rd.originChangesetIndex(), v);
		}
		intermediateEquals.clear();
		activeEquals = null;
		activeEqualsComesFromMerge = false;
		revisionDescriptor = null;
	}

	public void same(EqualBlock block) {
		intermediateEquals.add(block.originStart(), block.targetStart(), block.length());
	}

	public void added(AddBlock block) {
		RangeSeq rs = null;
		if (revisionDescriptor.isMerge() && block.originChangesetIndex() == revisionDescriptor.mergeChangesetIndex()) {
			rs = mergedRanges.get(revisionDescriptor.mergeChangesetIndex());
			if (rs == null) {
				mergedRanges.put(revisionDescriptor.mergeChangesetIndex(), rs = new RangeSeq());
			}
		}
		if (activeEquals.size() == 0) {
			return;
		}
		for (int i = 0, ln = block.firstAddedLine(), x = block.totalAddedLines(); i < x; i++, ln++) {
			int lnInFinal = activeEquals.mapLineIndex(ln);
			if (lnInFinal != -1/* && !knownLines[lnInFinal]*/) {
				if (rs != null) {
					rs.add(block.insertedAt() + i, lnInFinal, 1);
				} else {
					delegate.line(lnInFinal, block.targetChangesetIndex(), lineContent.elementAt(lnInFinal), new LineDescriptorImpl());
				}
				knownLines[lnInFinal] = true;
			}
		}
	}

	public void changed(ChangeBlock block) {
		added(block);
	}

	public void deleted(DeleteBlock block) {
	}

	private final class LineDescriptorImpl implements LineDescriptor {
		LineDescriptorImpl() {
		}

		public int totalLines() {
			return FileAnnotation.this.knownLines.length;
		}
	}
}