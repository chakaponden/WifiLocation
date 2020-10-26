#!/bin/bash
#
# Apply all Harbortouch patches in ARRAY_REPOS to AOSP sources
#
# usage:
# autopatch.sh <AOSP_SOURCE_TOP_PATH> 	    - to apply patches
# autopatch.sh <AOSP_SOURCE_TOP_PATH> -R	- to revert patches
#
# usage example:
# autopatch.sh /home/ubuntu/myandroid
#

# show readme
help()
{
   echo '#
# Apply all Harbortouch patches in ARRAY_REPOS to AOSP sources
#
# usage:
# autopatch.sh <AOSP_SOURCE_TOP_PATH> 	    - to apply patches
# autopatch.sh <AOSP_SOURCE_TOP_PATH> -R	- to revert patches
#
# usage example:
# autopatch.sh /home/ubuntu/myandroid
#'
}

# if help was called
if [ "$1" == "-h" ]; then
	help
	exit 0
fi

ANDROID_BUILD_TOP="$1"
CURDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PATCH_DIR="$CURDIR"
PATCH_EXTENSION=".patch"
GIT_COMMAND="apply"
GIT_COMMAND_ARG="$2"
ARRAY_REPOS="
/device/fsl
/frameworks/base
/packages/apps/Settings
"

if [ "$GIT_COMMAND_ARG" == "-R" ]; then
	# get files in folder in vise versa order by filename (Z-A)
	# to revert patches properly
	PATCHES_LIST_CMD="ls -1r"
	HARD_RESET_GIT_REPO=true
else
	# get files in folder in usual order by filename (A-Z)
	# to apply patches properly
	PATCHES_LIST_CMD="ls -1"
fi

for REPO_DIR in $ARRAY_REPOS
do
	if [ ! -d "$ANDROID_BUILD_TOP$REPO_DIR" ]; then
		# create REPO_DIR if directory doesn't exist
		mkdir -p $ANDROID_BUILD_TOP$REPO_DIR
	fi
	cd $PATCH_DIR$REPO_DIR
	# get all files in folder
	PATCHES_LIST=$($PATCHES_LIST_CMD)
	cd $ANDROID_BUILD_TOP$REPO_DIR
	for PATCH_FILENAME in $PATCHES_LIST
	do
		# apply only patches with extension ".patch"
		if [ ${PATCH_FILENAME: -6} == $PATCH_EXTENSION ]; then
			echo "Patching $REPO_DIR/$PATCH_FILENAME"
			git $GIT_COMMAND $GIT_COMMAND_ARG --whitespace=nowarn --apply --allow-binary-replacement "$PATCH_DIR$REPO_DIR/$PATCH_FILENAME"
		fi
	done
	# check variable is set and current directory contains git repository
	if [[ -n "$HARD_RESET_GIT_REPO" && -d ".git" ]]; then
		echo "Force reset --hard all modified files in git: $REPO_DIR"
		git reset --hard
		echo "Force clean new untracked files & dirs in git: $REPO_DIR"
		git clean -fd
	fi
done