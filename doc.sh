#!/bin/bash

# Keep a separate branch of generated API docs.
#
# This script generates API documentation, commits it to a separate branch, and
# pushes it upstream. It does this without actually checking out the branch,
# using a separate working tree directory, so without any disruption to your
# current working tree. You can have local file modifications, but the git index
# (staging area) must be clean.

# The git remote to fetch and push to. Also used to find the parent commit.
TARGET_REMOTE="origin"

# Branch name to commit and push to
TARGET_BRANCH="gh-pages"

# Command that generates the API docs
DOC_CMD="lein codox"

# Working tree directory. The output of $DOC_CMD must end up in this directory.
WORK_TREE="api-docs"

if ! git diff-index --quiet --cached HEAD ; then
    echo "Git index isn't clean. Make sure you have no staged changes. (try 'git reset .')"
    exit
fi

git fetch $TARGET_REMOTE
rm -rf $WORK_TREE
mkdir -p $WORK_TREE

echo "Generating docs"
$DOC_CMD

echo "Adding file to git index"
git --work-tree=$WORK_TREE add -A

TREE=`git write-tree`
echo "Created git tree $TREE"

if git show-ref --quiet --verify "refs/remotes/${TARGET_REMOTE}/${TARGET_BRANCH}" ; then
    PARENT=`git rev-parse ${TARGET_REMOTE}/${TARGET_BRANCH}`
    echo "Creating commit with parent ${PARENT} ${TARGET_REMOTE}/${TARGET_BRANCH}"
    COMMIT=`git commit-tree -p $PARENT $TREE -m 'Updating docs'`
else
    echo "Creating first commit of the branch"
    COMMIT=`git commit-tree $TREE -m 'Updating docs'`
fi

echo "Commit $COMMIT"
echo "Pushing to $TARGET_BRANCH"

git reset .
git push $TARGET_REMOTE $COMMIT:refs/heads/$TARGET_BRANCH
git fetch
echo
git log -1 --stat $TARGET_REMOTE/$TARGET_BRANCH
