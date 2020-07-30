#Hook 方式启动 Activity

## Activity 启动流程

## hook 启动原理

## adb

> adb shell dumpsys activity activities #看当前运行时 activity 栈
> 

## gradle 镜像库全局代理

```~/.gradle/init.gradle
allprojects {
    repositories {
        def ALIYUN_REPOSITORY_URL = 'http://maven.aliyun.com/nexus/content/groups/public'
        def ALIYUN_JCENTER_URL = 'http://maven.aliyun.com/nexus/content/repositories/jcenter'
        def ALIYUN_GOOGLE_URL = 'http://maven.aliyun.com/nexus/content/repositories/google'
        all { ArtifactRepository repo ->
            if(repo instanceof MavenArtifactRepository){
                def url = repo.url.toString()
                if (url.startsWith('https://repo1.maven.org/maven2')) {
                    project.logger.lifecycle "Repository ${repo.url} replaced aa by $ALIYUN_REPOSITORY_URL."
                    remove repo
                }
                if (url.startsWith('https://jcenter.bintray.com/')) {
                    project.logger.lifecycle "Repository ${repo.url} replaced bb by $ALIYUN_JCENTER_URL."
                    remove repo
                }
                if (url.startsWith('https://dl.google.com/dl/android/maven2/')) {
                    project.logger.lifecycle "Repository ${repo.url} replaced cc by $ALIYUN_GOOGLE_URL."
                    remove repo
                }
            }
        }
        maven { url ALIYUN_GOOGLE_URL }
        maven { url ALIYUN_REPOSITORY_URL }
        maven { url ALIYUN_JCENTER_URL }
    }
}
```