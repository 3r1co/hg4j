/*
 * Copyright (c) 2010-2012 TMate Software Ltd
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
package org.tmatesoft.hg.repo;

import static org.tmatesoft.hg.core.Nodeid.NULL;
import static org.tmatesoft.hg.repo.HgRepository.BAD_REVISION;
import static org.tmatesoft.hg.repo.HgRepository.TIP;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.hg.core.HgBadStateException;
import org.tmatesoft.hg.core.HgChangesetFileSneaker;
import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.core.HgInvalidControlFileException;
import org.tmatesoft.hg.core.HgInvalidRevisionException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.internal.DataAccess;
import org.tmatesoft.hg.internal.DigestHelper;
import org.tmatesoft.hg.internal.EncodingHelper;
import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.internal.IntMap;
import org.tmatesoft.hg.internal.IterateControlMediator;
import org.tmatesoft.hg.internal.Lifecycle;
import org.tmatesoft.hg.internal.Pool2;
import org.tmatesoft.hg.internal.RevlogStream;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.ProgressSupport;


/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgManifest extends Revlog {
	private RevisionMapper revisionMap;
	private EncodingHelper encodingHelper;
	
	/**
	 * File flags recorded in manifest
	 */
	public enum Flags {
		/**
		 * Executable bit set
		 */
		Exec,
		/**
		 * Symbolic link
		 */
		Link,
		/**
		 * Regular file
		 */
		RegularFile; 
		
		static Flags parse(String flags) {
			if ("x".equalsIgnoreCase(flags)) {
				return Exec;
			}
			if ("l".equalsIgnoreCase(flags)) {
				return Link;
			}
			if (flags == null) {
				return RegularFile;
			}
			throw new IllegalStateException(flags);
		}

		static Flags parse(byte[] data, int start, int length) {
			if (length == 0) {
				return RegularFile;
			}
			if (length == 1) {
				if (data[start] == 'x') {
					return Exec;
				}
				if (data[start] == 'l') {
					return Link;
				}
				// FALL THROUGH
			}
			throw new IllegalStateException(new String(data, start, length));
		}

		String nativeString() {
			if (this == Exec) {
				return "x";
			}
			if (this == Link) {
				return "l";
			}
			if (this == RegularFile) {
				return "";
			}
			throw new IllegalStateException(toString());
		}
	}

	/*package-local*/ HgManifest(HgRepository hgRepo, RevlogStream content, EncodingHelper eh) {
		super(hgRepo, content);
		encodingHelper = eh;
	}

	/**
	 * Walks manifest revisions that correspond to specified range of changesets. The order in which manifest versions get reported
	 * to the inspector corresponds to physical order of manifest revisions, not that of changesets (with few exceptions as noted below).
	 * That is, for cset-manifest revision pairs:
	 * <pre>
	 *   3  8
	 *   4  7
	 *   5  9
	 * </pre>
	 * call <code>walk(3,5, insp)</code> would yield (4,7), (3,8) and (5,9) to the inspector; 
	 * different order of arguments, <code>walk(5, 3, insp)</code>, makes no difference.
	 * 
	 * <p>Physical layout of mercurial files (revlog) doesn't impose any restriction on whether manifest and changeset revisions shall go 
	 * incrementally, nor it mandates presence of manifest version for a changeset. Thus, there might be changesets that record {@link Nodeid#NULL}
	 * as corresponding manifest revision. This situation is deemed exceptional now and what would <code>inspector</code> get depends on whether 
	 * <code>start</code> or <code>end</code> arguments point to such changeset, or such changeset happen to be somewhere inside the range 
	 * <code>[start..end]</code>. Implementation does it best to report empty manifests (<code>Inspector.begin(BAD_REVISION, NULL, csetRevIndex);</code>
	 * followed immediately by <code>Inspector.end(BAD_REVISION)</code> when <code>start</code> and/or <code>end</code> point to changeset with no associated 
	 * manifest revision. However, if changeset-manifest revision pairs look like:
	 * <pre>
	 *   3  8
	 *   4  -1 (cset records null revision for manifest)
	 *   5  9
	 * </pre>
	 * call <code>walk(3,5, insp)</code> would yield only (3,8) and (5,9) to the inspector, without additional empty 
	 * <code>Inspector.begin(); Inspector.end()</code> call pair.   
	 * 
	 * @param start changelog (not manifest!) revision to begin with
	 * @param end changelog (not manifest!) revision to stop, inclusive.
	 * @param inspector manifest revision visitor, can't be <code>null</code>
	 * @throws HgInvalidRevisionException if start or end specify non-existent revision index
	 * @throws IllegalArgumentException if start or end is not a revision index
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	public void walk(int start, int end, final Inspector inspector) throws HgInvalidRevisionException, HgInvalidControlFileException {
		if (inspector == null) {
			throw new IllegalArgumentException();
		}
		final int csetFirst = start <= end ? start : end, csetLast = start > end ? start : end;
		int manifestFirst, manifestLast, i = 0;
		do {
			manifestFirst = fromChangelog(csetFirst+i);
			if (manifestFirst == BAD_REVISION) {
				inspector.begin(BAD_REVISION, NULL, csetFirst+i);
				inspector.end(BAD_REVISION);
			}
			i++;
		} while (manifestFirst == BAD_REVISION && csetFirst+i <= csetLast);
		if (manifestFirst == BAD_REVISION) {
			getRepo().getContext().getLog().info(getClass(), "None of changesets [%d..%d] have associated manifest revision", csetFirst, csetLast);
			// we ran through all revisions in [start..end] and none of them had manifest.
			// we reported that to inspector and proceeding is done now.
			return;
		}
		i = 0;
		do {
			manifestLast = fromChangelog(csetLast-i);
			if (manifestLast == BAD_REVISION) {
				inspector.begin(BAD_REVISION, NULL, csetLast-i);
				inspector.end(BAD_REVISION);
			}
			i++;
		} while (manifestLast == BAD_REVISION && csetLast-i >= csetFirst);
		if (manifestLast == BAD_REVISION) {
			// hmm, manifestFirst != -1 here, hence there's i from [csetFirst..csetLast] for which manifest entry exists, 
			// and thus it's impossible to run into manifestLast == -1. Nevertheless, never hurts to check.
			throw new HgBadStateException(String.format("Manifest %d-%d(!) for cset range [%d..%d] ", manifestFirst, manifestLast, csetFirst, csetLast));
		}
		if (manifestLast < manifestFirst) {
			// there are tool-constructed repositories that got order of changeset revisions completely different from that of manifest
			int x = manifestLast;
			manifestLast = manifestFirst;
			manifestFirst = x;
		}
		content.iterate(manifestFirst, manifestLast, true, new ManifestParser(inspector, encodingHelper));
	}
	
	/**
	 * "Sparse" iteration of the manifest, more effective than accessing revisions one by one.
	 * <p> Inspector is invoked for each changeset revision supplied, even when there's no manifest
	 * revision associated with a changeset (@see {@link #walk(int, int, Inspector)} for more details when it happens). Order inspector
	 * gets invoked doesn't resemble order of changeset revisions supplied, manifest revisions are reported in the order they appear 
	 * in manifest revlog (with exception of changesets with missing manifest that may be reported in any order).   
	 * 
	 * @param inspector manifest revision visitor, can't be <code>null</code>
	 * @param revisionIndexes local indexes of changesets to visit, non-<code>null</code>
	 * @throws HgInvalidRevisionException if argument specifies non-existent revision index
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	public void walk(final Inspector inspector, int... revisionIndexes) throws HgInvalidRevisionException, HgInvalidControlFileException {
		if (inspector == null || revisionIndexes == null) {
			throw new IllegalArgumentException();
		}
		int[] manifestRevs = toManifestRevisionIndexes(revisionIndexes, inspector);
		content.iterate(manifestRevs, true, new ManifestParser(inspector, encodingHelper));
	}
	
	// 
	/**
	 * Tells manifest revision number that corresponds to the given changeset. May return {@link HgRepository#BAD_REVISION} 
	 * if changeset has no associated manifest (cset records NULL nodeid for manifest).
	 * @return manifest revision index, non-negative, or {@link HgRepository#BAD_REVISION}.
	 * @throws HgInvalidRevisionException if method argument specifies non-existent revision index
	 * @throws IllegalArgumentException if argument is not a revision index
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	/*package-local*/ int fromChangelog(int changesetRevisionIndex) throws HgInvalidRevisionException, HgInvalidControlFileException {
		if (HgInternals.wrongRevisionIndex(changesetRevisionIndex)) {
			throw new IllegalArgumentException(String.valueOf(changesetRevisionIndex));
		}
		if (changesetRevisionIndex == HgRepository.WORKING_COPY || changesetRevisionIndex == HgRepository.BAD_REVISION) {
			throw new IllegalArgumentException("Can't use constants like WORKING_COPY or BAD_REVISION");
		}
		// revisionNumber == TIP is processed by RevisionMapper 
		if (revisionMap == null) {
			revisionMap = new RevisionMapper(getRepo());
			content.iterate(0, TIP, false, revisionMap);
		}
		return revisionMap.at(changesetRevisionIndex);
	}
	
	/**
	 * Extracts file revision as it was known at the time of given changeset.
	 * For more thorough details about file at specific changeset, use {@link HgChangesetFileSneaker}.
	 * 
	 * @see HgChangesetFileSneaker
	 * @param changelogRevisionIndex local changeset index 
	 * @param file path to file in question
	 * @return file revision or <code>null</code> if manifest at specified revision doesn't list such file
	 * @throws HgInvalidRevisionException if method argument specifies non-existent revision index
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	public Nodeid getFileRevision(int changelogRevisionIndex, final Path file) throws HgInvalidRevisionException, HgInvalidControlFileException {
		// there's no need for HgDataFile to own this method, or get a delegate
		// as most of HgDataFile API is using file revision indexes, and there's easy step from file revision index to
		// both file revision and changeset revision index. But there's no easy way to go from changesetRevisionIndex to
		// file revision (the task this method solves), exept for HgFileInformer
		// I feel methods dealing with changeset indexes shall be more exposed in HgChangelog and HgManifest API.
		return getFileRevisions(file, changelogRevisionIndex).get(changelogRevisionIndex);
	}

	// XXX package-local or better API
	@Experimental(reason="Map as return value isn't that good")
	public Map<Integer, Nodeid> getFileRevisions(final Path file, int... changelogRevisionIndexes) throws HgInvalidRevisionException, HgInvalidControlFileException {
		// FIXME in fact, walk(Inspectr, path, int[]) might be better alternative than get()
		// TODO need tests
		int[] manifestRevisionIndexes = toManifestRevisionIndexes(changelogRevisionIndexes, null);
		IntMap<Nodeid> resMap = new IntMap<Nodeid>(changelogRevisionIndexes.length);
		content.iterate(manifestRevisionIndexes, true, new FileLookupInspector(encodingHelper, file, resMap, null));
		// IntMap to HashMap, 
		HashMap<Integer,Nodeid> rv = new HashMap<Integer, Nodeid>();
		resMap.fill(rv);
		return rv;
	}

	/**
	 * Extract file {@link Flags flags} as they were recorded in appropriate manifest version. 
	 *  
	 * @see HgDataFile#getFlags(int)
	 * @param changesetRevIndex changeset revision index
	 * @param file path to look up
	 * @return one of predefined enum values, or <code>null</code> if file was not known in the specified revision
	 * FIXME EXCEPTIONS
	 * @throws HgInvalidControlFileException
	 * @throws HgInvalidRevisionException 
	 */
	public Flags getFileFlags(int changesetRevIndex, Path file) throws HgInvalidRevisionException, HgInvalidControlFileException {
		int manifestRevIdx = fromChangelog(changesetRevIndex);
		IntMap<Flags> resMap = new IntMap<Flags>(2);
		content.iterate(manifestRevIdx, manifestRevIdx, true, new FileLookupInspector(encodingHelper, file, null, resMap));
		return resMap.get(changesetRevIndex);
	}


	/**
	 * @param changelogRevisionIndexes non-null
	 * @param inspector may be null if reporting of missing manifests is not needed
	 * @throws HgInvalidRevisionException if arguments specify non-existent revision index
	 * @throws IllegalArgumentException if any index argument is not a revision index
	 * @throws HgInvalidControlFileException if access to revlog index/data entry failed
	 */
	private int[] toManifestRevisionIndexes(int[] changelogRevisionIndexes, Inspector inspector) throws HgInvalidRevisionException, HgInvalidControlFileException {
		int[] manifestRevs = new int[changelogRevisionIndexes.length];
		boolean needsSort = false;
		int j = 0;
		for (int i = 0; i < changelogRevisionIndexes.length; i++) {
			final int manifestRevisionIndex = fromChangelog(changelogRevisionIndexes[i]);
			if (manifestRevisionIndex == BAD_REVISION) {
				if (inspector != null) {
					inspector.begin(BAD_REVISION, NULL, changelogRevisionIndexes[i]);
					inspector.end(BAD_REVISION);
				}
				// othrwise, ignore changeset without manifest
			} else {
				manifestRevs[j] = manifestRevisionIndex;
				if (j > 0 && manifestRevs[j-1] > manifestRevisionIndex) {
					needsSort = true;
				}
				j++;
			}
		}
		if (needsSort) {
			Arrays.sort(manifestRevs, 0, j);
		}
		if (j == manifestRevs.length) {
			return manifestRevs;
		} else {
			int[] rv = new int[j];
			//Arrays.copyOfRange
			System.arraycopy(manifestRevs, 0, rv, 0, j);
			return rv;
		}
	}

	public interface Inspector {
		boolean begin(int mainfestRevision, Nodeid nid, int changelogRevision);
		/**
		 * @deprecated switch to {@link Inspector2#next(Nodeid, Path, Flags)}
		 */
		@Deprecated
		boolean next(Nodeid nid, String fname, String flags);
		boolean end(int manifestRevision);
	}
	
	@Experimental(reason="Explore Path alternative for filenames and enum for flags")
	public interface Inspector2 extends Inspector {
		/**
		 * @param nid file revision
		 * @param fname file name
		 * @param flags one of {@link HgManifest.Flags} constants, not <code>null</code>
		 * @return <code>true</code> to continue iteration, <code>false</code> to stop
		 */
		boolean next(Nodeid nid, Path fname, Flags flags);
	}

	/**
	 * When Pool uses Strings directly,
	 * ManifestParser creates new String instance with new char[] value, and does byte->char conversion.
	 * For cpython repo, walk(0..10k), there are over 16 million filenames, of them only 3020 unique.
	 * This means there are 15.9 million useless char[] instances and byte->char conversions  
	 * 
	 * When String (Path) is wrapped into {@link PathProxy}, there's extra overhead of byte[] representation
	 * of the String, but these are only for unique Strings (Paths) (3020 in the example above). Besides, I save
	 * useless char[] and byte->char conversions. 
	 */
	private static class PathProxy {
		private byte[] data;
		private int start; 
		private final int hash, length;
		private Path result;
		private final EncodingHelper encHelper;

		public PathProxy(byte[] data, int start, int length, EncodingHelper eh) {
			this.data = data;
			this.start = start;
			this.length = length;
			this.encHelper = eh;

			// copy from String.hashCode(). In fact, not necessarily match result of String(data).hashCode
			// just need some nice algorithm here
			int h = 0;
			byte[] d = data;
			for (int i = 0, off = start, len = length; i < len; i++) {
				h = 31 * h + d[off++];
			}
			hash = h;
		}

		@Override
		public boolean equals(Object obj) {
			if (false == obj instanceof PathProxy) {
				return false;
			}
			PathProxy o = (PathProxy) obj;
			if (o.result != null && result != null) {
				return result.equals(o.result);
			}
			if (o.length != length || o.hash != hash) {
				return false;
			}
			for (int i = 0, x = o.start, y = start; i < length; i++) {
				if (o.data[x++] != data[y++]) {
					return false;
				}
			}
			return true;
		}
		@Override
		public int hashCode() {
			return hash;
		}
		
		public Path freeze() {
			if (result == null) {
				result = Path.create(encHelper.fromManifest(data, start, length));
				// release reference to bigger data array, make a copy of relevant part only
				// use original bytes, not those from String above to avoid cache misses due to different encodings 
				byte[] d = new byte[length];
				System.arraycopy(data, start, d, 0, length);
				data = d;
				start = 0;
			}
			return result;
		}
	}

	private static class ManifestParser implements RevlogStream.Inspector, Lifecycle {
		private final Inspector inspector;
		private final Inspector2 inspector2;
		private Pool2<Nodeid> nodeidPool, thisRevPool;
		private final Pool2<PathProxy> fnamePool;
		private byte[] nodeidLookupBuffer = new byte[20]; // get reassigned each time new Nodeid is added to pool
		private final ProgressSupport progressHelper;
		private IterateControlMediator iterateControl;
		private final EncodingHelper encHelper;
		
		public ManifestParser(Inspector delegate, EncodingHelper eh) {
			assert delegate != null;
			inspector = delegate;
			inspector2 = delegate instanceof Inspector2 ? (Inspector2) delegate : null;
			encHelper = eh;
			nodeidPool = new Pool2<Nodeid>();
			fnamePool = new Pool2<PathProxy>();
			thisRevPool = new Pool2<Nodeid>();
			progressHelper = ProgressSupport.Factory.get(delegate);
		}
		
		public void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess da) throws HgException {
			try {
				if (!inspector.begin(revisionNumber, new Nodeid(nodeid, true), linkRevision)) {
					iterateControl.stop();
					return;
				}
				if (!da.isEmpty()) {
					// although unlikely, manifest entry may be empty, when all files have been deleted from the repository
					Path fname = null;
					Flags flags = null;
					Nodeid nid = null;
					int i;
					byte[] data = da.byteArray();
					for (i = 0; i < actualLen; i++) {
						int x = i;
						for( ; data[i] != '\n' && i < actualLen; i++) {
							if (fname == null && data[i] == 0) {
								PathProxy px = fnamePool.unify(new PathProxy(data, x, i - x, encHelper));
								// if (cached = fnamePool.unify(px))== px then cacheMiss, else cacheHit
								// cpython 0..10k: hits: 15 989 152, misses: 3020
								fname = px.freeze();
								x = i+1;
							}
						}
						if (i < actualLen) {
							assert data[i] == '\n'; 
							int nodeidLen = i - x < 40 ? i-x : 40; // if > 40, there are flags
							DigestHelper.ascii2bin(data, x, nodeidLen, nodeidLookupBuffer); // ignore return value as it's unlikely to have NULL in manifest
							nid = new Nodeid(nodeidLookupBuffer, false); // this Nodeid is for pool lookup only, mock object
							Nodeid cached = nodeidPool.unify(nid);
							if (cached == nid) {
								// buffer now belongs to the cached nodeid
								nodeidLookupBuffer = new byte[20];
							} else {
								nid = cached; // use existing version, discard the lookup object
							} // for cpython 0..10k, cache hits are 15 973 301, vs 18871 misses.
							thisRevPool.record(nid); // memorize revision for the next iteration. 
							if (nodeidLen + x < i) {
								// 'x' and 'l' for executable bits and symlinks?
								// hg --debug manifest shows 644 for each regular file in my repo
								// for cpython 0..10k, there are 4361062 flag checks, and there's only 1 unique flag
								flags = Flags.parse(data, x + nodeidLen, i-x-nodeidLen);
							} else {
								flags = Flags.RegularFile;
							}
							boolean good2go;
							if (inspector2 == null) {
								String flagString = flags == Flags.RegularFile ? null : flags.nativeString();
								good2go = inspector.next(nid, fname.toString(), flagString);
							} else {
								good2go = inspector2.next(nid, fname, flags);
							}
							if (!good2go) {
								iterateControl.stop();
								return;
							}
						}
						nid = null;
						fname = null;
						flags = null;
					}
				}
				if (!inspector.end(revisionNumber)) {
					iterateControl.stop();
					return;
				}
				//
				// keep only actual file revisions, found at this version 
				// (next manifest is likely to refer to most of them, although in specific cases 
				// like commit in another branch a lot may be useless)
				nodeidPool.clear();
				Pool2<Nodeid> t = nodeidPool;
				nodeidPool = thisRevPool;
				thisRevPool = t;
				iterateControl.checkCancelled();
				progressHelper.worked(1);
			} catch (IOException ex) {
				throw new HgException(ex);
			}
		}

		public void start(int count, Callback callback, Object token) {
			CancelSupport cs = CancelSupport.Factory.get(inspector, null);
			iterateControl = new IterateControlMediator(cs, callback);
			progressHelper.start(count);
		}

		public void finish(Object token) {
			progressHelper.done();
		}
	}
	
	private static class RevisionMapper implements RevlogStream.Inspector, Lifecycle {
		
		private final int changelogRevisionCount;
		private int[] changelog2manifest;
		private final HgRepository repo;

		public RevisionMapper(HgRepository hgRepo) {
			repo = hgRepo;
			changelogRevisionCount = repo.getChangelog().getRevisionCount();
		}

		/**
		 * Get index of manifest revision that corresponds to specified changeset
		 * @param changesetRevisionIndex non-negative index of changelog revision, or {@link HgRepository#TIP}
		 * @return index of manifest revision, or {@link HgRepository#BAD_REVISION} if changeset doesn't reference a valid manifest
		 * @throws HgInvalidRevisionException if method argument specifies non-existent revision index
		 */
		public int at(int changesetRevisionIndex) throws HgInvalidRevisionException {
			if (changesetRevisionIndex == TIP) {
				changesetRevisionIndex = changelogRevisionCount - 1;
			}
			if (changesetRevisionIndex >= changelogRevisionCount) {
				throw new HgInvalidRevisionException(changesetRevisionIndex);
			}
			if (changelog2manifest != null) {
				return changelog2manifest[changesetRevisionIndex];
			}
			return changesetRevisionIndex;
		}

		// XXX likely can be replaced with Revlog.RevisionInspector
		public void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess data) {
			if (changelog2manifest != null) {
				// next assertion is not an error, rather assumption check, which is too development-related to be explicit exception - 
				// I just wonder if there are manifests that have two entries pointing to single changeset. It seems unrealistic, though -
				// changeset records one and only one manifest nodeid
				assert changelog2manifest[linkRevision] == BAD_REVISION : String.format("revision:%d, link:%d, already linked to revision:%d", revisionNumber, linkRevision, changelog2manifest[linkRevision]);
				changelog2manifest[linkRevision] = revisionNumber;
			} else {
				if (revisionNumber != linkRevision) {
					changelog2manifest = new int[changelogRevisionCount];
					Arrays.fill(changelog2manifest, BAD_REVISION);
					for (int i = 0; i < revisionNumber; changelog2manifest[i] = i, i++)
						;
					changelog2manifest[linkRevision] = revisionNumber;
				}
			}
		}
		
		public void start(int count, Callback callback, Object token) {
			if (count != changelogRevisionCount) {
				assert count < changelogRevisionCount; // no idea what to do if manifest has more revisions than changelog
				// the way how manifest may contain more revisions than changelog, as I can imagine, is a result of  
				// some kind of an import tool (e.g. from SVN or CVS), that creates manifest and changelog independently.
				// Note, it's pure guess, I didn't see such repository yet (although the way manifest revisions
				// in cpython repo are numbered makes me think aforementioned way) 
				changelog2manifest = new int[changelogRevisionCount];
				Arrays.fill(changelog2manifest, BAD_REVISION);
			}
		}

		public void finish(Object token) {
			if (changelog2manifest == null) {
				return;
			}
			// I assume there'd be not too many revisions we don't know manifest of
			ArrayList<Integer> undefinedChangelogRevision = new ArrayList<Integer>();
			for (int i = 0; i < changelog2manifest.length; i++) {
				if (changelog2manifest[i] == BAD_REVISION) {
					undefinedChangelogRevision.add(i);
				}
			}
			for (int u : undefinedChangelogRevision) {
				try {
					Nodeid manifest = repo.getChangelog().range(u, u).get(0).manifest();
					// TODO calculate those missing effectively (e.g. cache and sort nodeids to speed lookup
					// right away in the #next (may refactor ParentWalker's sequential and sorted into dedicated helper and reuse here)
					if (manifest.isNull()) {
						repo.getContext().getLog().warn(getClass(), "Changeset %d has no associated manifest entry", u);
						// keep -1 in the changelog2manifest map.
					} else {
						changelog2manifest[u] = repo.getManifest().getRevisionIndex(manifest);
					}
				} catch (HgInvalidControlFileException ex) {
					// FIXME need to propagate the error up to client  
					repo.getContext().getLog().error(getClass(), ex, null);
				}
			}
		}
	}
	
	/**
	 * Look up specified file in possibly multiple manifest revisions, collect file revision and flags.
	 */
	private static class FileLookupInspector implements RevlogStream.Inspector {
		
		private final byte[] filenameAsBytes;
		private final IntMap<Nodeid> csetIndex2FileRev;
		private final IntMap<Flags> csetIndex2Flags;

		public FileLookupInspector(EncodingHelper eh, Path fileToLookUp, IntMap<Nodeid> csetIndex2FileRevMap, IntMap<Flags> csetIndex2FlagsMap) {
			assert fileToLookUp != null;
			// need at least one map for the inspector to make any sense
			assert csetIndex2FileRevMap != null || csetIndex2FlagsMap != null;
			csetIndex2FileRev = csetIndex2FileRevMap;
			csetIndex2Flags = csetIndex2FlagsMap;
			filenameAsBytes = eh.toManifest(fileToLookUp.toString());
		}
		
		public void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess data) throws HgException {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try {
				byte b;
				while (!data.isEmpty() && (b = data.readByte()) != '\n') {
					if (b != 0) {
						bos.write(b);
					} else {
						byte[] byteArray = bos.toByteArray();
						bos.reset();
						if (Arrays.equals(filenameAsBytes, byteArray)) {
							if (csetIndex2FileRev != null) {
								byte[] nid = new byte[40];  
								data.readBytes(nid, 0, 40);
								csetIndex2FileRev.put(linkRevision, Nodeid.fromAscii(nid, 0, 40));
							} else {
								data.skip(40);
							}
							if (csetIndex2Flags != null) {
								while (!data.isEmpty() && (b = data.readByte()) != '\n') {
									bos.write(b);
								}
								Flags flags;
								if (bos.size() == 0) {
									flags = Flags.RegularFile;
								} else {
									flags = Flags.parse(bos.toByteArray(), 0, bos.size());
								}
								csetIndex2Flags.put(linkRevision, flags);
							}
							break;
						} else {
							data.skip(40);
						}
						// else skip to the end of line
						while (!data.isEmpty() && (b = data.readByte()) != '\n')
							;
					}
				}
			} catch (IOException ex) {
				throw new HgException(ex); // FIXME EXCEPTIONS
			}
		}
	}
}
