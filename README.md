# XinHua-Dictionary-To-SQL
将 中华新华字典数据 转换到可被导入到 MySQL 的 sql 文件.

数据来自 https://github.com/pwxcoo/chinese-xinhua

导出的 sql 文件总量为 53.6MiB.

导出的文件在本仓库的 `/sql` 目录, 可以直接下载.

# 编译与运行
本程序使用 Kotlin 编写, 需要首先安装 JDK.

之后执行以下命令

    ./gradlew shadowJar

默认输出路径为项目目录的 `/build/libs`

程序的默认输入目录为 `./chinese-xinhua/data`

默认输出路径为 `./sql`

使用命令行参数覆盖默认设置, 命令行格式如下

    java -jar xinhua-dictionary-to-sql-all.jar {inputDir} {outputDir}

可以直接从仓库的 `release` 页面下载 `jar` 文件.

# Copyright
如有侵权, 请抓数据作者!
