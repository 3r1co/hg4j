/*
 * Copyright (c) 2010 Artem Tikhomirov 
 */
package com.tmate.hgkit.console;

import com.tmate.hgkit.fs.RepositoryLookup;
import com.tmate.hgkit.ll.HgRepository;
import com.tmate.hgkit.ll.LocalHgRepo;

/**
 *
 * @author artem
 */
public class Status {

	public static void main(String[] args) throws Exception {
		RepositoryLookup repoLookup = new RepositoryLookup();
		RepositoryLookup.Options cmdLineOpts = RepositoryLookup.Options.parse(args);
		HgRepository hgRepo = repoLookup.detect(cmdLineOpts);
		if (hgRepo.isInvalid()) {
			System.err.printf("Can't find repository in: %s\n", hgRepo.getLocation());
			return;
		}
		System.out.println(hgRepo.getLocation());
		((LocalHgRepo) hgRepo).loadDirstate().dump();
	}
}
