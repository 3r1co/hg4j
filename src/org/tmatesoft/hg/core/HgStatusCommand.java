/*
 * Copyright (c) 2011-2012 TMate Software Ltd
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

import static org.tmatesoft.hg.core.HgStatus.Kind.*;
import static org.tmatesoft.hg.repo.HgInternals.wrongRevisionIndex;
import static org.tmatesoft.hg.repo.HgRepository.*;

import java.io.IOException;
import java.util.ConcurrentModificationException;

import org.tmatesoft.hg.internal.ChangelogHelper;
import org.tmatesoft.hg.internal.Internals;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.repo.HgRuntimeException;
import org.tmatesoft.hg.repo.HgStatusCollector;
import org.tmatesoft.hg.repo.HgStatusInspector;
import org.tmatesoft.hg.repo.HgWorkingCopyStatusCollector;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.Path;
import org.tmatesoft.hg.util.Outcome;

/**
 * Command to obtain file status information, 'hg status' counterpart. 
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgStatusCommand extends HgAbstractCommand<HgStatusCommand> {
	private final HgRepository repo;

	private int startRevision = TIP;
	private int endRevision = WORKING_COPY;
	private Path.Matcher scope;
	
	private final Mediator mediator = new Mediator();

	public HgStatusCommand(HgRepository hgRepo) { 
		repo = hgRepo;
		defaults();
	}

	public HgStatusCommand defaults() {
		final Mediator m = mediator;
		m.needModified = m.needAdded = m.needRemoved = m.needUnknown = m.needMissing = true;
		m.needCopies = m.needClean = m.needIgnored = false;
		return this;
	}
	public HgStatusCommand all() {
		final Mediator m = mediator;
		m.needModified = m.needAdded = m.needRemoved = m.needUnknown = m.needMissing = true;
		m.needCopies = m.needClean = m.needIgnored = true;
		return this;
	}
	

	public HgStatusCommand modified(boolean include) {
		mediator.needModified = include;
		return this;
	}
	public HgStatusCommand added(boolean include) {
		mediator.needAdded = include;
		return this;
	}
	public HgStatusCommand removed(boolean include) {
		mediator.needRemoved = include;
		return this;
	}
	public HgStatusCommand deleted(boolean include) {
		mediator.needMissing = include;
		return this;
	}
	public HgStatusCommand unknown(boolean include) {
		mediator.needUnknown = include;
		return this;
	}
	public HgStatusCommand clean(boolean include) {
		mediator.needClean = include;
		return this;
	}
	public HgStatusCommand ignored(boolean include) {
		mediator.needIgnored = include;
		return this;
	}
	
	/**
	 * If set, either base:revision or base:workingdir
	 * to unset, pass {@link HgRepository#TIP} or {@link HgRepository#BAD_REVISION}
	 * @param changesetRevisionIndex - local index of a changeset to base status from
	 * @return <code>this</code> for convenience
	 * @throws IllegalArgumentException when revision is negative or {@link HgRepository#WORKING_COPY} 
	 */
	public HgStatusCommand base(int changesetRevisionIndex) {
		if (changesetRevisionIndex == WORKING_COPY || wrongRevisionIndex(changesetRevisionIndex)) {
			throw new IllegalArgumentException(String.valueOf(changesetRevisionIndex));
		}
		if (changesetRevisionIndex == BAD_REVISION) {
			changesetRevisionIndex = TIP;
		}
		startRevision = changesetRevisionIndex;
		return this;
	}
	
	/**
	 * Revision without base == --change
	 * Pass {@link HgRepository#WORKING_COPY} or {@link HgRepository#BAD_REVISION} to reset
	 * @param changesetRevisionIndex - non-negative changeset revision local index, or any of {@link HgRepository#BAD_REVISION}, {@link HgRepository#WORKING_COPY} or {@link HgRepository#TIP}  
	 * @return <code>this</code> for convenience
	 * @throws IllegalArgumentException if revision index doesn't specify legitimate revision. 
	 */
	public HgStatusCommand revision(int changesetRevisionIndex) {
		if (changesetRevisionIndex == BAD_REVISION) {
			changesetRevisionIndex = WORKING_COPY;
		}
		if (wrongRevisionIndex(changesetRevisionIndex)) {
			throw new IllegalArgumentException(String.valueOf(changesetRevisionIndex));
		}
		endRevision = changesetRevisionIndex;
		return this;
	}
	
	/**
	 * Shorthand for {@link #base(int) cmd.base(BAD_REVISION)}{@link #change(int) .revision(revision)}
	 *  
	 * @param changesetRevisionIndex compare given revision against its parent
	 * @return <code>this</code> for convenience
	 */
	public HgStatusCommand change(int changesetRevisionIndex) {
		base(BAD_REVISION);
		return revision(changesetRevisionIndex);
	}
	
	/**
	 * Limit status operation to certain sub-tree.
	 * 
	 * @param scopeMatcher - matcher to use,  pass <code>null/<code> to reset
	 * @return <code>this</code> for convenience
	 */
	public HgStatusCommand match(Path.Matcher scopeMatcher) {
		scope = scopeMatcher;
		return this;
	}

	public HgStatusCommand subrepo(boolean visit) {
		throw Internals.notImplemented();
	}

	/**
	 * Perform status operation according to parameters set.
	 *  
	 * @param statusHandler callback to get status information
 	 * @throws HgCallbackTargetException propagated exception from the handler
	 * @throws HgException subclass thereof to indicate specific issue with the command arguments or repository state
	 * @throws IOException if there are (further unspecified) errors while walking working copy
	 * @throws CancelledException if execution of the command was cancelled
	 * @throws IllegalArgumentException if handler is <code>null</code>
	 * @throws ConcurrentModificationException if this command already runs (i.e. being used from another thread)
	 */
	public void execute(HgStatusHandler statusHandler) throws HgCallbackTargetException, HgException, IOException, CancelledException {
		if (statusHandler == null) {
			throw new IllegalArgumentException();
		}
		if (mediator.busy()) {
			throw new ConcurrentModificationException();
		}
		HgStatusCollector sc = new HgStatusCollector(repo); // TODO from CommandContext
//		PathPool pathHelper = new PathPool(repo.getPathHelper()); // TODO from CommandContext
		try {
			// XXX if I need a rough estimation (for ProgressMonitor) of number of work units,
			// I may use number of files in either rev1 or rev2 manifest edition
			mediator.start(statusHandler, getCancelSupport(statusHandler, true), new ChangelogHelper(repo, startRevision));
			if (endRevision == WORKING_COPY) {
				HgWorkingCopyStatusCollector wcsc = scope != null ? HgWorkingCopyStatusCollector.create(repo, scope) : new HgWorkingCopyStatusCollector(repo);
				wcsc.setBaseRevisionCollector(sc);
				wcsc.walk(startRevision, mediator);
			} else {
				sc.setScope(scope); // explicitly set, even if null - would be handy once we reuse StatusCollector
				if (startRevision == TIP) {
					sc.change(endRevision, mediator);
				} else {
					sc.walk(startRevision, endRevision, mediator);
				}
			}
		} catch (CancelledException ex) {
			// this is our exception, thrown from Mediator.
			// next check shall throw original cause of the stop - either HgCallbackTargetException or original CancelledException
			mediator.checkFailure();
		} catch (HgRuntimeException ex) {
			throw new HgLibraryFailureException(ex);
		} finally {
			mediator.done();
		}
	}

	private class Mediator implements HgStatusInspector, CancelSupport {
		boolean needModified;
		boolean needAdded;
		boolean needRemoved;
		boolean needUnknown;
		boolean needMissing;
		boolean needClean;
		boolean needIgnored;
		boolean needCopies;
		HgStatusHandler handler;
		private ChangelogHelper logHelper;
		private CancelSupport handlerCancelSupport;
		private HgCallbackTargetException failure;
		private CancelledException cancellation;

		Mediator() {
		}
		
		public void start(HgStatusHandler h, CancelSupport hcs, ChangelogHelper changelogHelper) {
			handler = h;
			handlerCancelSupport = hcs;
			logHelper = changelogHelper;
		}

		public void done() {
			handler = null;
			handlerCancelSupport = null;
			logHelper = null;
			failure = null;
			cancellation = null;
		}
		
		public boolean busy() {
			return handler != null;
		}

		// XXX copy from ChangesetTransformer. Perhaps, can share the code?
		public void checkFailure() throws HgCallbackTargetException, CancelledException {
			// do not forget to clear exceptions for reuse of this instance 
			if (failure != null) {
				HgCallbackTargetException toThrow = failure;
				failure = null; 
				throw toThrow;
			}
			if (cancellation != null) {
				CancelledException toThrow = cancellation;
				cancellation = null;
				throw toThrow;
			}
		}

		// XXX copy from ChangesetTransformer. code sharing note above applies
		public void checkCancelled() throws CancelledException {
			if (failure != null || cancellation != null) {
				// stop status iteration. Our exception is for the purposes of cancellation only,
				// the one we have stored (this.cancellation) is for user
				throw new CancelledException(); 
			}
		}

		private void dispatch(HgStatus s) {
			try {
				handler.status(s);
				handlerCancelSupport.checkCancelled();
			} catch (HgCallbackTargetException ex) {
				failure = ex;
			} catch (CancelledException ex) {
				cancellation = ex;
			}
		}

		public void modified(Path fname) {
			if (needModified) {
				dispatch(new HgStatus(Modified, fname, logHelper));
			}
		}
		public void added(Path fname) {
			if (needAdded) {
				dispatch(new HgStatus(Added, fname, logHelper));
			}
		}
		public void removed(Path fname) {
			if (needRemoved) {
				dispatch(new HgStatus(Removed, fname, logHelper));
			}
		}
		public void copied(Path fnameOrigin, Path fnameAdded) {
			if (needCopies) {
				// TODO post-1.0 in fact, merged files may report 'copied from' as well, correct status kind thus may differ from Added
				dispatch(new HgStatus(Added, fnameAdded, fnameOrigin, logHelper));
			}
		}
		public void missing(Path fname) {
			if (needMissing) {
				dispatch(new HgStatus(Missing, fname, logHelper));
			}
		}
		public void unknown(Path fname) {
			if (needUnknown) {
				dispatch(new HgStatus(Unknown, fname, logHelper));
			}
		}
		public void clean(Path fname) {
			if (needClean) {
				dispatch(new HgStatus(Clean, fname, logHelper));
			}
		}
		public void ignored(Path fname) {
			if (needIgnored) {
				dispatch(new HgStatus(Ignored, fname, logHelper));
			}
		}
		
		public void invalid(Path fname, Exception err) {
			try {
				handler.error(fname, new Outcome(Outcome.Kind.Failure, "Failed to get file status", err));
				handlerCancelSupport.checkCancelled();
			} catch (HgCallbackTargetException ex) {
				failure = ex;
			} catch (CancelledException ex) {
				cancellation = ex;
			}
		}
	}
}
