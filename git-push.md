# Git repos sync to SMB shared folder

**Session ID:** ses_04d29ac0effeEawMjTncmvlMte
**Created:** 7/30/2026, 12:43:38 PM
**Updated:** 7/30/2026, 2:09:10 PM

---

## User

I am running a git project on a VM using VMWare workstation and I have a mapped Shared Folder over SMB which is connected to the host where changes are then commited to a subversion repository. I would like to plumb in a basic mechanism to sync the files from the VM git repos to the host Shared folder so I can then use the subversion native to the host to send and commit the changes. It should send the change or files from the git project to the shared folder, for example when I do a push, so mapping a remote to a directory, hence in this case the Shared folder rather than a true remote like github. Is this possible 

---

## Assistant

Yes — this is exactly what Git's local/file-based remotes are for, and there's a specific config that makes `git push` sync *working files* (not just git objects) into a directory. This is the cleanest fit for your VM→Shared Folder→SVN bridge.

## The mechanism: non-bare repo + `updateInstead`

Git can push to a local path (or SMB-mounted path) just like a remote URL. By default, pushing into a *non-bare* repo (one with a working tree) is rejected because Git won't clobber a checked-out branch. The `receive.denyCurrentBranch updateInstead` flag flips this: on push, Git updates the working tree files in-place to match the incoming commit. That's precisely "push → files appear in the shared folder."

## Setup

**1. On the shared folder (do this once, from the VM since it's mounted there):**
```bash
cd /mnt/hgfs/Shared/myproject        # your SMB mount path
git init                              # non-bare repo, NOT --bare
git config receive.denyCurrentBranch updateInstead
git config core.fileMode false        # SMB mangles exec bit; see caveats
```

**2. In your VM working repo:**
```bash
cd ~/projects/myproject
git remote add host /mnt/hgfs/Shared/myproject
git push host main                    # or master, or whatever your branch is
```

After the push, the files in `/mnt/hgfs/Shared/myproject` on the host reflect the pushed commit's tree. From the host, run your `svn add`/`svn commit` against whatever directory the host uses for SVN.

## How this maps to your mental model

- `host` is a real Git remote — it just points at a directory instead of `github.com`. `git remote -v`, `git push host`, `git fetch host` all work normally.
- Only files changed between commits get rewritten in the working tree (Git doesn't blow away the whole dir), so it's a delta sync, not a full copy.
- You can push multiple branches; the working tree follows whichever branch the shared-folder repo has checked out (`git -C /mnt/hgfs/Shared/myproject checkout <branch>` on the VM side to switch).

## Caveats worth knowing up front

- **SMB + file modes**: SMB doesn't preserve Unix permissions or the executable bit reliably. Set `core.fileMode false` on the shared-folder repo (done above) so Git stops seeing phantom mode changes. Symlinks may also flatten — avoid them in this repo, or set `core.symlinks false`.
- **Line endings**: if the host is Windows, CRLF translation can corrupt things. Add a `.gitattributes` with `* text=auto` (or `* -text` to force binary passthrough) in the VM repo before pushing.
- **`.svn` metadata collision**: if the shared folder *is* the SVN working copy (not a staging area you copy from), don't `git init` there — Git will try to track `.svn/` and SVN will see `.git/`. Either (a) keep the git repo in a sibling dir and `rsync -a --exclude=.git --exclude=.svn` into the SVN wc, or (b) add `.svn/` to `.git/info/exclude` and `.git/` to `svn:ignore` and accept the mess. Option (a) is cleaner.
- **Atomicity over SMB**: large pushes can be slow and occasionally flaky; if a push fails mid-way, just re-run it — `updateInstead` is idempotent.
- **No history rewriting safety**: force-pushes will re-point the shared tree. That's fine for a bridge, just be aware.

## Alternatives if `updateInstead` doesn't fit

- **Bare repo on the share + `git archive`/checkout on host**: push to a bare repo, then on the host `git --git-dir=/share/proj.git archive HEAD | tar -x -C svn_wc/`. More steps, but no working-tree-on-SMB issues.
- **post-receive hook that rsyncs**: keep the bare repo on the share, add a hook that `rsync`s the exported tree into the SVN working copy. Gives you full control over excludes (`.git`, `.svn`, etc.).
- **Plain rsync, no git remote**: `rsync -a --delete --exclude=.git ~/projects/myproject/ /mnt/hgfs/Shared/myproject/` on demand. Simplest, but loses the "push triggers it" semantics you asked for.

## Recommendation

Start with the `updateInstead` approach — it's the closest to what you described ("map a remote to a directory, push syncs files"). If you hit SMB permission/CRLF pain or the `.svn` collision, drop down to the bare-repo + rsync-hook variant.

Want me to write the actual config files and a small `post-receive` hook for the rsync variant as a fallback, or are you set with the `updateInstead` setup?

---

## User

I am set on the updateInstead, the main issue would be just to ignore the .git or .svn depending on which end

---

## Assistant

Good — that's the one real seam to seal. Both ends need a local ignore so neither VCS tries to swallow the other's metadata. Here's the exact config.

## Git side (shared folder repo) — ignore `.svn`

The shared folder repo is non-bare with a working tree, so it could "see" the `.svn/` dir the host created. Use `.git/info/exclude` (local-only, never committed, never pushed) rather than `.gitignore`:

```bash
echo ".svn/" >> /mnt/hgfs/Shared/myproject/.git/info/exclude
```

That's it for git. `updateInstead` only touches *tracked* files on push, so `.svn/` is never rewritten or deleted by a push — it's just prevented from ever being `git add`-ed by accident.

## SVN side (host) — ignore `.git`

Two options, recommend the first:

**Option 1 — global ignores (cleanest, host-local only):**
Edit `~/.subversion/config` on the host, under `[miscellany]`:
```ini
[miscellany]
global-ignores = .git
```
If `global-ignores` already has values, append `.git` to the space-separated list. This keeps the `.git` ignore private to your host config — it never gets committed into SVN, so other SVN consumers don't see a weird `.git` ignore entry.

**Option 2 — per-directory `svn:ignore` (visible to all SVN users):**
```bash
svn propset svn:ignore .git /path/to/svn/wc/myproject
svn commit /path/to/svn/wc/myproject -m "Ignore git bridge metadata"
```

## Critical ordering

Do the SVN ignore config **before** the first `svn add`/`svn commit` of the shared folder. If `.git/` gets committed into SVN first, you'll have to unwind it with `svn rm --keep-local .git` later — annoying but recoverable.

## One workflow note on deletions

When a git commit *removes* a file, `updateInstead` deletes it from the shared folder working tree. SVN will then show that path as missing (`!` in `svn status`). You handle it on the host with:
```bash
svn delete --keep-local <path>   # or just svn rm after confirming
svn commit
```
That's the manual step in the bridge — git deletions don't auto-translate to `svn delete`. Additions and modifications flow through automatically; deletions need a one-liner on the host. Worth knowing going in so it doesn't surprise you.

Your VM source repo (`~/projects/myproject`) needs none of this — it's a plain git repo, no `.svn` present. Only the shared folder repo and the host SVN config get the ignores.

---

## User

so from VMWware SLES linux VM I would cd to the host shared folder which hosts the subversion repository, run git init and ignore .svn through info/exclude (which presumably is a global generally default set of git ignores).  Subversion on the host then needs to ignore the .git directory and then then from the source git repo on the linux SLES VM you just add the remote?

---

## Assistant

Yes, that's the flow exactly — with one terminology correction and one ordering caveat.

## Correction: `.git/info/exclude` is repo-local, not global

`.git/info/exclude` applies **only to that one repo** (the shared-folder repo). It's "untracked by git" (never committed, never pushed), which is why it's the right place for `.svn/` — but it's not a global ignore. The truly global git ignore is `core.excludesFile` (usually `~/.config/git/ignore`). For your case, repo-local is what you want anyway, so `.git/info/exclude` is correct — just know it's scoped to that single repo, not all your git repos.

## The flow, confirmed

1. **On the VM**, cd into the mounted shared folder (which is the SVN working copy) and:
   ```bash
   cd /mnt/hgfs/Shared/myproject   # the SVN wc
   git init
   git config receive.denyCurrentBranch updateInstead
   git config core.fileMode false
   echo ".svn/" >> .git/info/exclude
   ```
2. **On the host**, make SVN ignore `.git` **before** any `svn add`/`svn commit`:
   - global: add `.git` to `global-ignores` in `~/.subversion/config` under `[miscellany]`, or
   - per-dir: `svn propset svn:ignore .git .` then commit that prop change.
3. **On the VM**, from your source repo:
   ```bash
   cd ~/projects/myproject
   git remote add host /mnt/hgfs/Shared/myproject
   git push host main
   ```

## The one ordering trap

Since the shared folder *is* the SVN working copy, `.git/` lands inside it the moment you run `git init` in step 1. If you do `svn add`/`svn commit` on the host before setting the `.git` ignore in step 2, SVN will happily commit the entire `.git/` tree — which you do not want. So: **set the SVN ignore for `.git` first, then do your first `svn add`/`svn commit`.** If you get the order wrong, `svn rm --keep-local .git` unwinds it.

That's the whole bridge. Push from the VM, files appear in the shared folder, SVN on the host sees them as normal working-copy changes.

---

## User

and only the files modified are transmitted and of course would obey the source repo gitignore for example

---

## Assistant

Both correct, with two nuances worth pinning down.

## Only modified files transmitted — yes, with one caveat

Git push sends only the **new objects** the destination lacks — i.e., blobs for files that changed between the old and new commit, plus the commit/tree objects. Unchanged files are neither re-sent nor re-touched in the working tree. `updateInstead` diffs old-tree vs new-tree and rewrites only the differing paths.

**Caveat — the first push sends everything.** The shared-folder repo starts empty (just `git init`), so relative to "nothing," every file is new. That first `git push host main` writes the full tree. Every push after is a delta. So over SMB the initial sync is full; ongoing syncs are incremental.

Also note "modified" is at file granularity, not byte granularity: a 100 MB file that changes by one byte still transmits a fresh ~100 MB blob (packfile delta-compression helps internally, but don't count on it over SMB). Fine for source; worth knowing if you have large binary assets in the repo.

## Source repo `.gitignore` obeyed — yes, and it's a one-way filter

Files ignored in the source repo are never tracked, never in a commit, never pushed — so they never appear in the shared folder, period. You do **not** need a parallel `svn:ignore` for them; they simply don't cross the bridge. The source `.gitignore` is the single source of truth for what gets bridged.

Two small footnotes:

- **The `.gitignore` file itself crosses the bridge** if it's committed in the source repo (which it normally is). SVN on the host will then see a `.gitignore` file as a normal tracked file. Harmless — it's just text — but if you don't want it in SVN, add `.gitignore` to `svn:ignore` on the host.
- **The shared-folder repo's own `.git/info/exclude`** (where you put `.svn/`) is separate and only matters if you ever `git add` directly in the shared folder — which you won't; it's a passive receiver. So no interaction with the source's `.gitignore`.

Net: push is incremental after the first sync, and the source `.gitignore` is the filter that decides what ever reaches SVN. Clean one-way bridge.

---

## User

I have posted my current solution, which is through Emacs adding an advice around git push and git pull and I have received the following from those that have read it : I'm sure someone has already informed you of this, but I just read [1]
and wanted to make sure you know that git supports using local
directories as remotes. I.e., you can

    git remote add usb /media/my-usb

and then push/pull/clone to/from to the "usb" remote.

This is much more reliable than, e.g., rsync.

- Steven : Hi, I've read you latest emacs blogpost and I'm just wondering if you
know that you can just:

git remote add originOrWhatever /some/network/mount/or/usb/orWhatever

or:

git remote add sshOrigin ssh://user@machine:/full/path/to/git/repo/

Now, I don't know what OS is inside that VM, how is your network drive
mounted or what is in those scripts. But it feels weird that you
wouldn't mention this if you knew about this potential solution. 

---

## Assistant

They're right, and it's exactly the mechanism we walked through — `git remote add host /path/to/share` plus `receive.denyCurrentBranch updateInstead`. Both commenters are describing the same thing from different angles: Steven emphasizes "local dir as remote, more reliable than rsync," and the second commenter generalizes it to any path or SSH URL. Neither is wrong.

## Why their point lands

Your Emacs advice wraps `git push`/`git pull` to do a file sync (rsync or copy, presumably) to the shared folder. That works, but it's a *parallel* transport sitting *beside* git — it has to re-derive what changed, re-decide what to send, and it can drift from git's own view of the tree (e.g., it might copy ignored files, miss deletions, or race with a half-finished push). The native remote approach makes git itself the transport, so:

- Delta logic is git's, not yours — only changed blobs go over SMB.
- Deletions, renames, mode changes are handled by the tree diff, not a script.
- `.gitignore` is enforced for free; no second filter to keep in sync.
- `git status`, `git log`, `git fetch` against the `host` remote all just work — you can even pull *back* from the host if you ever edit there.
- No advice to maintain across Emacs/git versions.

So the commenters aren't being pedantic; they're pointing at a real robustness gap. The advice approach is a fine hack, but it's strictly more fragile than letting git do what it's built for.

## Where your Emacs layer still earns its keep

It's not useless — it just shouldn't be doing the *transport*. Two ways to keep the convenience without the fragility:

**1. Automatic dual-push.** If you want `git push` (to `origin`, say github) to *also* sync to the shared folder in one keystroke, keep the advice but make it just call `git push host` after the real push:

```elisp
(defun my/git-push-also-host (&rest _)
  (when (member "origin" (my/git-current-remotes))
    (call-process "git" nil nil nil "push" "host")))
(advice-add 'magit-push :after #'my/git-push-also-host)
```

Now git does both pushes natively; your advice only *orchestrates*, it doesn't transport. That's the right division of labor.

**2. Or skip advice entirely with git's own multi-push.** Git can push to several remotes at once:

```bash
git remote add host /mnt/hgfs/Shared/myproject
git remote set-url --add --push origin <github-url>
git remote set-url --add --push origin /mnt/hgfs/Shared/myproject
```

Now `git push origin` hits both. No Emacs code at all. Caveat: this rebinds `origin`'s push URLs, which some find surprising; the advice approach keeps `origin` clean and adds `host` as an explicit second remote.

## How I'd respond to them

Something like:

> Thanks — yes, I'm aware git supports local/SSH-path remotes, and that's actually the approach I'd recommend over rsync. The Emacs advice in the post was about *convenience* (auto-syncing to the shared folder on every push without typing a second command), not about reinventing git's transport. The cleaner version keeps the advice as a thin orchestrator that runs `git push host` after the main push, letting git handle the delta/deletion/ignore logic itself — which is exactly the `receive.denyCurrentBranch updateInstead` setup on a local-dir remote. I should've made that distinction clearer in the post.

That acknowledges they're right, clarifies your actual intent (convenience, not reinventing transport), and points readers at the better pattern — which is the one we set up.

---

