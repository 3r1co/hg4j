/**
 * Copyright (c) 2010 Artem Tikhomirov 
 */
package com.tmate.hgkit.ll;

/**
 *
 * @author artem
 */
public abstract class Revlog {

	private final HgRepository hgRepo;
	protected final RevlogStream content;

	protected Revlog(HgRepository hgRepo, RevlogStream content) {
		if (hgRepo == null) {
			throw new NullPointerException();
		}
		this.hgRepo = hgRepo;
		this.content = content;
	}

	public final HgRepository getRepo() {
		return hgRepo;
	}

	public int getRevisionCount() {
		return content.revisionCount();
	}

	public interface Inspector {
		// XXX boolean retVal to indicate whether to continue?
		void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[/*32*/] nodeid, byte[] data);
	}
}
