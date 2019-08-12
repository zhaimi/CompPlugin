package com.zhaimi.plugin

import java.util.regex.Pattern

/**
 * 一些配置常量
 */
class Constant {
    static String MAIN_MODULE = "main.module"
    static String RUN_ALONE = "run.alone"
    static String DEPEND_MODULE_DEBUG = "depend.module.debug"
    static String DEPEND_MODULE_RELEASE = "depend.module.release"

    static boolean isMavenArtifact(String str) {
        if (str == null || str.isEmpty()) {
            return false
        }
        return Pattern.matches("\\S+(\\.\\S+)+:\\S+(:\\S+)?(@\\S+)?", str)
    }

    static boolean isLocalPath(String str) {
        if (str == null || str.isEmpty()) {
            return false
        }
        return str.contains("/")
    }
}