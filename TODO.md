Roadmap for 1.0
===============

Story: Init database
--------------------

- Status: DONE
- The `init` command works
- All the stuff in `session.clj` works except filelock and file-root-path

Story: Install a Single Package on a Fresh Database
---------------------------------------------------

- Status: BLACK BOX PASSING
- Other stuff to do:
  - [x] Linting, formatting
  - [ ] Write tests, get them to pass
  - [ ] Write docs rough draft

- The `add` command works
- All the stuff in `session.clj` works, including filelock and file-root-path.
  REMEMBER to use java.nio FileLock

- Functions:

  - `zic.fs/download` : install downloads a package
  - TODO: TEST the above with mock, to ensure it gives http the proper options

  - `zic.fs/unpack`: install unpacks it
  - TODO: TEST the above with integration/fs-level test.

  - `zic.db/add-pkg`: Install adds to database
  - TODO: TEST the above with integration/fs-level test.

  - `zic.db/serialize-pkg`: ditto
  - TODO: TEST the above unit-wise.

  - `zic.package/install-package`: shell
  - TODO: TEST the above with black-box larger integration test.

  - `zic.cli/add!`: shell
  - TODO: test the above with integration test.

Story: Info on Package
----------------------

- The `info` command works
- STATUS: BLACK BOX PASSING
- Functions:
  - `zic.cli/info`
  - `zic.db/package-info!`
  - `zic.db/deserialize-pkg`

Story: Files of a Package
-------------------------

- STATUS: BLACK BOX PASSING
- The `files` command works
- Functions:
  - `zic.cli/files`

Story: Verify a Package
-----------------------

- STATUS: BLACK BOX PASSING
- A column in the files section for crc
- Functions:
  - `zic.cli/verify`

Story: Install package next to existing packages
------------------------------------------------

- Functions:
  - `zic.db/file-owned?`
  - `zic.db/list-archive-files`
  - `zic.package/package-installable?`

Story: Update a Package that Already Exists via Install
------------------------------------------------------

  - `zic.db/pkg-exists?`
  - `zic.db/lookup-pkg-id`
  - `zic.db/erase-pkg`
  - `zic.fs/rm-files`
  - `zic.db/list-files`
  - `zic.package/remove-without-cascade` and its use in install-package

Story: Remove Package(s)
------------------------

- The `remove` command works
- Functions needed that aren't already done:
  - `zic.cli/remove`
  - `zic.package/remove-cascade`
  - `zic.db/used-by`

Story: List Orphans
-------------------

- The `orphans` command works
- An `is_source` column in the package table
- Functions:
  - `zic.package/graph-sources` and its use in install-packages
  - `zic.cli/orphans`
  - `zic.db/non-source-pkgs`
  - `zic.db/used-somewhere?`
