Gradle插件上传Maven仓库步骤:

1.注册 bintray



2.Add New Repository 创建maven







3: 修改相关的module 的gradle

    apply plugin: 'groovy'  // 如果是gradle插件,需要使用该插件
    //apply plugin: 'com.android.library' //如果是android sdk,就使用该插件
    
    apply plugin: 'maven'
    
    apply plugin:'com.jfrog.bintray'  // 需要使用bintray
    
    dependencies {
        compile gradleApi()
        compile localGroovy()
    }
    
    version = "0.1"   // 项目的版本
    
    def siteUrl = 'https://github.com/zhaimi/CompPlugin'   //项目的仓库地址
    def gitUrl = 'https://github.com/zhaimi/CompPlugin.git'   //项目的仓库地址
    
    group = "com.zhaimi.plugin"   //项目的group ID
    
    install {
        repositories.mavenInstaller {
            pom {
                project {
                    packaging 'aar'   
                    name 'Less Code For Android'
                    url siteUrl
                    licenses {
                        license {
                            name 'The Apache Software License, Version 2.0'//刚才创建仓库时的许可证
                            url 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                        }
                    }
                    developers {
                        developer {
                            id 'zhaimi'
                            name 'zhaimi'
                            email '15002824200@163.com'
                        }
                    }
                    scm {
                        connection gitUrl
                        developerConnection gitUrl
                        url siteUrl
                    }
                }
            }
        }
    }
    
    if(project.hasProperty("android")){  //如果是android的sdk,会走此处
        task sourcesJar(type: Jar) {
            from android.sourceSets.main.java.srcDirs
            classifier = 'sources'
        }
    
        task javadoc(type: Javadoc) {
            source = android.sourceSets.main.java.srcDirs
            classpath += project.files(android.getBootClasspath().join(File.pathSeparator))
        }
    }else {			//非android项目,就将所有源码都打包上去
        task sourcesJar(type:Jar,dependsOn:classes){
            classifier ='sources'
        }
    }
    
    
    task javadocJar(type: Jar, dependsOn: javadoc) {
        classifier = 'javadoc'
        from javadoc.destinationDir
    }
    
    artifacts {
        archives javadocJar
        archives sourcesJar
    }
    
    Properties properties = new Properties()
    boolean isHasFile = false
    if (project.rootProject.file('loca') != null){
        isHasFile = true
        properties.load(project.rootProject.file('local.properties').newDataInputStream())
    }
    bintray{
        user = isHasFile ? properties.getProperty("bintray.user") : System.getenv("bintray.user")
        key = isHasFile ? properties.getProperty("bintray.apikey") : System.getenv("bintray.apikey")
        configurations = ['archives']
        pkg {
            repo = "comp"//仓库名称
            name = "comp"//项目名称
            websiteUrl = siteUrl
            vcsUrl = gitUrl
            licenses = ["Apache-2.0"]
            publish = true
        }
    }

4:修改local.properties

分别添加我们在bintray 申请的 userId和 apikey

    bintray.user=Bintray上的user
    bintray.apikey=Bintray上的apikey



5:上传代码到仓库

 运行: gradlew bintrayUpload

6:同步代码到JCenter
