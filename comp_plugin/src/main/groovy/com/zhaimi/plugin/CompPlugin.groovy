package com.zhaimi.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

import static com.zhaimi.plugin.Constant.*

/**
 * 组件plugin
 */
class CompPlugin implements Plugin<Project> {

    void apply(Project project) {
        if (!project.rootProject.hasProperty(MAIN_MODULE)) {
            throw new RuntimeException("you should set $MAIN_MODULE in root project's gradle.properties")
        }

        String mainModule = project.rootProject.property(MAIN_MODULE)
        println("main module is: $mainModule")

        String currentModule = project.path.replace(":", "")
        println("current module is: $currentModule")

        List<String> taskNames = project.gradle.startParameter.taskNames
        AssembleTask assembleTask = getTaskInfo(taskNames)
        String compileModule = fetchCurrentModule(project, assembleTask)
        println("compile module is: $compileModule")

        def runAlone = false
        if (currentModule == mainModule) {
            // 对于主项目，runAlone强制修改为true，这就意味着组件不能引用主项目，这在层级结构里面也是这么规定的
            runAlone = true

        } else {
            if (project.hasProperty(RUN_ALONE)) {
                runAlone = Boolean.parseBoolean((project.properties.get(RUN_ALONE)))
            }

            // 对于runAlone==true的情况需要根据实际情况修改其值，但如果是false，则不用修改
            if (runAlone) {
                // 针对配置了runAlone=true且不是mainModule的module判断是否定义了独立运行的AndroidManifest.xml，否则不能独立运行
                def manifestPath = "${project.rootDir}/$currentModule/src/runalone/AndroidManifest.xml"
                File manifest = new File(manifestPath)
                runAlone = manifest.exists() && manifest.isFile()
                if (!runAlone) {
                    println("[$currentModule] manifest is not exist: $manifestPath")
                }
            }

            // 对于要编译的组件，runAlone修改为true，其他组件都强制修改为false
            if (runAlone && assembleTask.isAssemble) {
                runAlone = currentModule == compileModule
            }
        }
        println("$currentModule runAlone: $runAlone")

        // 根据配置添加各种组件依赖
        if (runAlone) {
            project.apply plugin: 'com.android.application'
            if (currentModule != mainModule) {
                project.android.sourceSets.main {
                    manifest.srcFile 'src/runalone/AndroidManifest.xml'
                    java.srcDirs += ['src/runalone/java', 'src/runalone/kotlin']
                    res.srcDirs += 'src/runalone/res'
                    assets.srcDirs += 'src/runalone/assets'
                    jniLibs.srcDirs += 'src/runalone/jniLibs'
                }
            }
            println("$currentModule apply plugin 'com.android.application'")
            if (assembleTask.isAssemble && currentModule == compileModule) {
                dependComponents(project, assembleTask)
//                project.android.registerTransform(new CompCodeTransform(project))
            }
        } else {
            project.apply plugin: 'com.android.library'
            println("$currentModule apply plugin 'com.android.library'")
        }

        // 默认支持kotlin目录
        project.android.sourceSets.main {
            java.srcDirs += 'src/main/kotlin'
        }
    }

    /**
     * 根据当前的task，获取要运行的组件，规则如下：
     * assembleRelease ---app
     * app:assembleRelease :app:assembleRelease ---app
     * component:assembleRelease :component:assembleRelease ---component
     *
     * @param project
     * @param assembleTask
     */
    private String fetchCurrentModule(Project project, AssembleTask assembleTask) {
        if (assembleTask.module != null && assembleTask.module.length() > 0) {
            return assembleTask.module
        } else {
            return project.rootProject.property(MAIN_MODULE)
        }
    }

    /**
     * 自动添加依赖，只在运行assemble任务的才会添加依赖，因此在开发期间组件之间是完全感知不到的，这是做到完全隔离的关键
     * 支持三种语法：
     * 直接引用工程: module
     * maven: groupId:artifactId:version(@aar)
     * 本地aar: ../comp/xxx.aar
     *
     * @param assembleTask
     * @param project
     */
    private void dependComponents(Project project, AssembleTask assembleTask) {
        def dependComponentStr
        if (assembleTask.isDebug) {
            dependComponentStr = project.properties.get(DEPEND_MODULE_DEBUG)
        } else {
            dependComponentStr = project.properties.get(DEPEND_MODULE_RELEASE)
        }

        if (dependComponentStr == null || dependComponentStr.length() == 0) {
            println("there is no dependencies ")
            return
        }

        def depends = dependComponentStr.split(",")
        if (depends == null || depends.length == 0) {
            println("there is no dependencies ")
            return
        }

        depends.each { depend ->
            depend = depend.trim()
            if (isMavenArtifact(depend)) {
                // 是否是maven地址
                /**
                 * 示例语法:groupId:artifactId:version(@aar)
                 * depend.module.debug=com.weibo.cd:library:1.0.0
                 * 注意，前提是已经将组件aar文件发布到maven上，并配置了相应的repositories
                 */
                project.dependencies.add("implementation", depend)
                println("add dependencies lib:$depend")

            } else if (isLocalPath(depend)) {
                // 是否是本地aar地址
                /**
                 * 示例语法:../xx/component.aar)
                 * depend.module.debug=component.aar
                 */
                def file = project.file(depend)
                if (file.exists()) {
                    project.dependencies.add("implementation", project.files(depend))
                    println("add dependencies aar: $depend")
                } else {
                    throw new RuntimeException("$depend not found! maybe you should generate a new one.")
                }

            } else {
                // 默认直接引用工程
                /**
                 * 示例语法:module
                 * depend.module.debug=component1,component2
                 */
                project.dependencies.add("implementation", project.project(':' + depend))
                println("add dependencies project:$depend")
            }
        }
    }

    private AssembleTask getTaskInfo(List<String> tasks) {
        def assembleTask = new AssembleTask()
        for (String task : tasks) {
            if (task.toUpperCase().contains("ASSEMBLE")
                    || task.contains("aR")
                    || task.contains("asR")
                    || task.contains("asD")
                    || task.toUpperCase().contains("TINKER")
                    || task.toUpperCase().contains("INSTALL")
                    || task.toUpperCase().contains("RESGUARD")) {

                assembleTask.isAssemble = true
                assembleTask.isDebug = task.toUpperCase().contains("DEBUG")
                String[] names = task.split(":")
                // 两种形式 component:assembleRelease :component:assembleRelease
                assembleTask.module = names.length > 1 ? names[names.length - 2] : ""
                break
            }
        }
        return assembleTask
    }

    private class AssembleTask {
        boolean isAssemble = false
        boolean isDebug = false
        String module = null
    }
}