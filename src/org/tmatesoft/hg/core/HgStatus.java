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
package org.tmatesoft.hg.core;

import java.util.Date;

import org.tmatesoft.hg.internal.ChangelogHelper;
import org.tmatesoft.hg.repo.HgChangelog.Changeset;

public class HgStatus {

	public enum Kind {
		Modified, Added, Removed, Unknown, Missing, Clean, Ignored
	};

	private final HgStatus.Kind kind;
	private final Path path;
	private final Path origin;
	private final ChangelogHelper logHelper;
		
	HgStatus(HgStatus.Kind kind, Path path, ChangelogHelper changelogHelper) {
		this(kind, path, null, changelogHelper);
	}

	HgStatus(HgStatus.Kind kind, Path path, Path copyOrigin, ChangelogHelper changelogHelper) {
		this.kind = kind;
		this.path  = path;
		origin = copyOrigin;
		logHelper = changelogHelper;
	}

	public HgStatus.Kind getKind() {
		return kind;
	}

	public Path getPath() {
		return path;
	}

	public Path getOriginalPath() {
		return origin;
	}

	public boolean isCopy() {
		return origin != null;
	}

	/**
	 * @return <code>null</code> if author for the change can't be deduced (e.g. for clean files it's senseless)
	 */
	public String getModificationAuthor() {
		Changeset cset = logHelper.findLatestChangeWith(path);
		if (cset == null) {
			if (kind == Kind.Modified || kind == Kind.Added || kind == Kind.Removed /*&& RightBoundary is TIP*/) {
				return logHelper.getNextCommitUsername();
			}
		} else {
			return cset.user();
		}
		return null;
	}

	public Date getModificationDate() {
		Changeset cset = logHelper.findLatestChangeWith(path);
		if (cset == null) {
			// FIXME check dirstate and/or local file for tstamp
			return new Date(); // what's correct 
		} else {
			return cset.date();
		}
	}
}