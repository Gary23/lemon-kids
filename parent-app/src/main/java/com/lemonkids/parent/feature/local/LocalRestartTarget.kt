package com.lemonkids.parent.feature.local

internal fun isLocalRestartTarget(
    targetPid: Int,
    targetUid: Int,
    targetName: String,
    ownPid: Int,
    ownUid: Int,
    packageName: String
): Boolean = targetPid > 0 && targetPid != ownPid && targetUid == ownUid && targetName == packageName
