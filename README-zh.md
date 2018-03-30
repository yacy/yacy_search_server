# YaCy
[![Build Status](https://travis-ci.org/yacy/yacy_search_server.svg?branch=master)](https://travis-ci.org/yacy/yacy_search_server)

[![Deploy to Docker Cloud](https://files.cloud.docker.com/images/deploy-to-dockercloud.svg)](https://cloud.docker.com/stack/deploy/?repo=https://github.com/yacy/yacy_search_server/tree/master/docker)
[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)

[English](./README.md)

## 这是什么?

YaCy是一款采用了新的搜索方法的搜索引擎软件。
它不需要中央服务器。相反，它的搜索结果
来自独立的分布式网络。在这样的分布式网络中，
没有任何一个实体可以决定列出的内容或结果出现的顺序。

YaCy搜索引擎在每个单独用户的计算机上运行。搜索词语
在从用户的计算机中发出之前进行散列计算。不同与传统
搜索引擎，YaCy更加注重保护用户的隐私。
用户的计算机可以使用YaCy创建个人的搜索索引和
排名，以便结果更好地匹配用户随着时间的推移寻找的内容。
YaCy只需点击几下，就可以轻松创建一个定制的搜索门户。

每个YaCy用户，要么作为大型搜索网络的一部分（YaCy包含一个
对等网络协议与其它安装了YaCy搜索引擎的用户
交换搜索索引），要么运行YaCy建立
一个公共或私人的个人搜索门户。

YaCy搜索门户也可以放在内部网络环境中，因为
YaCy可作为商业企业搜索解决方案的替代品。 它具有的网络
扫描器可以轻松发现所有可用的http，ftp和smb服务器。

为创建一个网络索引，YaCy给每个人提供了一个网络爬虫，
它不会审查也不会有中央服务器储存这些爬虫数据：
- 搜索网页（自动使用所有其他YaCy同伴）
- 合作爬行; 支持其他爬虫
- 内网索引和搜索
- 建立你自己的搜索门户
- 所有用户都有相同的权利
- 匿名化用户搜索的综合措施

为了能够使用YaCy网络执行搜索功能，每个用户都必须
建立自己的节点。 更多的人使用，将意味着更高的索引容量
和更好的分布式索引性能。


## 许可

YaCy在GPL v2协议下发布
源代码位于发行包内 (见/source和/htroot)。


## 在哪里查看更多文档？

文档可在以下网址找到：
- (主页)        https://yacy.net/
- (德国论坛)     http://forum.yacy.de/
- (Wiki:de)          http://www.yacy-websuche.de/wiki/index.php/De:Start
- (Wiki:en)          http://www.yacy-websearch.net/wiki/index.php/En:Start
- (教学视频)  https://yacy.net/en/Tutorials.html and https://yacy.net/de/Lehrfilme.html

每个位置都有一个（YaCy）搜索功能，
这个搜索功能将所有这些位置的搜索合并到一个搜索结果中。


## 依赖？ 我需要什么其他软件？

您需要Java 1.8或更高版本才能运行YaCy，没有别的了 (Java 1.7仍然可以用来运行主要的 [1.92/9000 发行版本](https://github.com/yacy/yacy_search_server/releases/tag/Release_1.92) )
请从这下载 https://www.java.com

YaCy也可以运行在Iced Tea 3。
见 https://icedtea.classpath.org

不需要其他软件了！
(你不需要apache，tomcat或者mysql或者别的什么)


## 我如何启动这个软件？

YaCy的启动和关闭：

- 在GNU/Linux和OpenBSD上:
启动: 执行 ./startYACY.sh
关闭: 执行 ./stopYACY.sh

- 在Windows上:
启动: 双击 startYACY.bat
关闭: 双击 stopYACY.bat

- 在Mac OS X上:
请使用Mac应用程序，并像其他Mac应用程序那样
启动或停止它（双击）


## 如何使用此软件，管理界面在哪里？

YaCy是在Web服务器上构建的。 在你启动YaCy后,
打开你的浏览器并浏览

   http://localhost:8090

在这儿你可以看到你的个人搜索和管理界面。


## 如果我在服务器上安装YaCy（没显示器）会怎么样？

你可以做到这一点，但YaCy自动授权用户，如果他们
从本地主机访问服务器。大约10分钟后，一个随机
密码产生，然后就无法远程登录了。
如果您在不是您的服务器上安装YaCy工作站，那么你必须在
第一次启动后立即设置一个管理帐户。打开:

http://<远程服务器地址>:8090/ConfigAccounts_p.html

并设置管理帐户。

## 我可以在虚拟机或容器中运行YaCy吗？

YaCy看在由VirtualBox或VMware等软件管理的虚拟机中正常工作。

容器技术可能更加灵活轻便，并且YaCy也可以在其中正常工作。

这些技术既可以在本地部署，也可以在您拥有的远程机器上部署，也可以在“云”中部署。 确定最符合您的隐私要求的是什么。

### Docker

使用本页面顶部的部署按钮，轻松在您选择的Docker云提供商（可以是您拥有的机器）上部署YaCy。

YaCy在Docker中的更多详细信息见 [docker/Readme.md](docker/Readme.md).

### Heroku

使用顶部的部署按钮轻松部署[Heroku](https://www.heroku.com/) PaaS (服务平台)。

YaCy在Heroku中的更多详细信息见[Heroku.md](Heroku.md).


## 8090端口不好，人们不能进入该端口

您可以使用iptables将端口80转发到8090：
iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8090
在某些操作系统上，您必须先启用对您正在使用的端口的访问权限：
iptables -I INPUT -m tcp -p tcp --dport 8090 -j ACCEPT


## 我怎样才能拓展它; 需要多少内存; 磁盘空间？

YaCy可以在自己的搜索索引中扩展到数百万个网页。
内存的默认分配是600MB，它分配给java进程，
但并没一直这么多。 GC进程偶尔将释放内存。
如果您的索引很小（即大约100000页）
那么你可以分配_更少_内存（即200MB），但是如果你的索引扩展到
多达100多万个网页，那么你应该开始增加
内存分配。打开 http://localhost:8090/Performance_p.html
并分配更多/更少的内存。
如果您的搜索索引中有数百万个网页，那么您可能会
分配有千兆字节的磁盘空间。 您可以减少磁盘使用
即将htcache空间设置为不同的大小; 要这么做，只需
打开 http://localhost:8090/ConfigHTCache_p.html 并设置一个新值。


## 加入开发组！

YaCy是在许多人的帮助下创建的。大约30名程序员已经帮忙，
你可以在这儿看到他们的名单: https://yacy.net/en/Join.html
欢迎加入我们!


## 如何获得源代码以及如何自己编译YaCy？

源代码位于每个YaCy版本中。 你也可以通过克隆存储库
从这 https://github.com/yacy/yacy_search_server 得到YaCy

```
git clone https://github.com/yacy/yacy_search_server
```

请克隆我们的代码并帮助开发！
该代码遵从GPL v2协议。

编译YaCy：
- 您需要Java 1.8或更高版本和[Apache Ant](https://ant.apache.org/)
- 仅需编译: "ant clean all" - 然后你可以 "./startYACY.sh" 或者 "./startYACY.bat"
- 创建一个发布tarball: "ant dist"
- 创建一个Mac OS版本: "ant distMacApp" (works only on a Mac)
- 创建一个debian版本: "ant deb"
- 用Eclipse工作: 在eclipse中你还需要启动ant构建过程
  因为servlet页面不是由eclipse构建过程编译的
在dist程序之后，release可以在RELEASE子目录中找到

使用Maven构建：
- 第一次goto子目录libbuild（其中包含maven父级pom）
- 用“mvn clean install -DskipTests”进行编译，这将创建所有需要的模块
- 在上面之后，您可以使用主目录中的pom来使用maven构建YaCy

## 是否有任何API或如何在YaCy上附加软件？

YaCy中有许多内置接口，它们都基于http/xml和
HTTP/JSON。 如果您注意到在YaCy中某些网页的右上角的橙色“API”图标，
您就会发现这些接口。只需点击它，然后
你会看到刚才网页上的xml/json版本。
另一种方法是使用/bin中提供的shell脚本子目录。
这也会调用Web界面。 通过克隆它们，
您可以轻松地创建更多的shell API访问方法。

## 联系

我们的主要联络点是德国的论坛 http://forum.yacy.net
我们鼓励您使用您自己的语言创建YaCy论坛。

如果您有任何问题，请不要犹豫，联系维护人员：
发送电子邮件给Michael Christen（mc@yacy.net），提供一个包括'yacy'这个词的有意义主题
来防止你的邮件被卡在我的反垃圾邮件过滤器中。

如果你喜欢有特殊需求的定制版本，
随时向作者提出一个定制YaCy的商业建议
根据你的需求，我们还提供集成解决方案
以实现集成到您的企业应用程序中。

Germany, Frankfurt a.M., 26.11.2011
Michael Peter Christen
