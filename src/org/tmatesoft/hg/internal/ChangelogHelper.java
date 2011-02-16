/*
 * Copyright (c) 2011 TMate Software Ltd
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

import java.util.TreeMap;

import org.tmatesoft.hg.core.Path;
import org.tmatesoft.hg.repo.HgChangelog.Changeset;
import org.tmatesoft.hg.repo.HgDataFile;
import org.tmatesoft.hg.repo.HgInternals;
import org.tmatesoft.hg.repo.HgRepository;

/**
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class ChangelogHelper {
	private final int leftBoundary;
	private final HgRepository repo;
	private final TreeMap<Integer, Changeset> cache = new TreeMap<Integer, Changeset>();

	/**
	 * @param hgRepo
	 * @param leftBoundaryRevision walker never visits revisions with local numbers less than specified,
	 * IOW only revisions [leftBoundaryRevision..TIP] are considered.
	 */
	public ChangelogHelper(HgRepository hgRepo, int leftBoundaryRevision) {
		repo = hgRepo;
		leftBoundary = leftBoundaryRevision;
	}
	
	/**
	 * @return the repo
	 */
	public HgRepository getRepo() {
		return repo;
	}

	/**
	 * Walks changelog in reverse order
	 * @param file
	 * @return changeset where specified file is mentioned among affected files, or 
	 * <code>null</code> if none found up to leftBoundary 
	 */
	public Changeset findLatestChangeWith(Path file) {
		HgDataFile df = repo.getFileNode(file);
		int changelogRev = df.getChangesetLocalRevision(HgRepository.TIP);
		if (changelogRev >= leftBoundary) {
			// the method is likely to be invoked for different files, 
			// while changesets might be the same. Cache 'em not to read too much. 
			Changeset cs = cache.get(changelogRev);
			if (cs == null) {
				cs = repo.getChangelog().range(changelogRev, changelogRev).get(0);
				cache.put(changelogRev, cs);
			}
			return cs;
		}
		return null;
	}

	public String getNextCommitUsername() {
		return new HgInternals(repo).getNextCommitUsername();
	}
}
