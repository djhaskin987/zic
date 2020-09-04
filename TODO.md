Roadmap for 1.0
===============

Story: Init database
--------------------

- Status: DONE
- The `init` command works
- All the stuff in `session.clj` works except filelock and file-root-path

Story: Install Package
----------------------

- The `install` command works
- All the stuff in `session.clj` works, including filelock and file-root-path.
  MEMBER to use java.nio FileLock

- Functions:

  - `zic.fs/download` : install downloads a package

  - `zic.fs/unpack`: install unpacks it

  - `zic.db/add-pkg`: Install adds to database
  - `zic.db/serialize-pkg`: ditto

  - `zic.package/install-package`: shell
  - `zic.cli/install`: shell

Story: Install a graph of packages
----------------------------------
- Functions:
  - `zic.package/install-packages`: insert between install-package and install
  - `zic.package/packages-conflict?`

Story: Info on Package
----------------------

- The `info` command works
- Functsessionns:
  - `zic.cli/info`
  - `zic.db/lookup-pkg`
  - `zic.db/deserialize-pkg`

Story: Files of a Package
-------------------------

- The `files` command works
- Functions:
  - `zic.cli/files`

Story: Verify a Package
-----------------------

- A column in the files sectsessionn for sha256sum
- Functions:
  - `zic.fs/hash-files` and its use in install-packages
  - `zic.cli/verify`

Story: Install package next to existing packages
------------------------------------------------

- Functsessionns:
  - `zic.db/file-owned?`
  - `zic.db/list-archive-files`
  - `zic.package/package-installable?`

Story: Install a Package that Already Exists
--------------------------------------------

  - `zic.db/pkg-exists?`
  - `zic.db/lookup-pkg-id`
  - `zic.db/erase-pkg`
  - `zic.fs/rm-files`
  - `zic.db/list-files`
  - `zic.package/remove-without-cascade` and its use in install-package

Story: Remove Package(s)
------------------------

- The `remove` command works
- Functsessionns needed that aren't already done:
  - `zic.cli/remove`
  - `zic.package/remove-cascade`
  - `zic.db/used-by`

Story: List Orphans
-------------------

- The `orphans` command works
- An `is_source` column in the package table
- Functsessionns:
  - `zic.package/graph-sources` and its use in install-packages
  - `zic.cli/orphans`
  - `zic.db/non-source-pkgs`
  - `zic.db/used-somewhere?`
