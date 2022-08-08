# MapReduce: Simplified Data Processing on Large Clusters（MapReduce: 简化大型集群下的数据处理）

##### 作者：Jeffrey Dean and Sanjay Ghemawat

### Abstract（摘要）
######
MapReduce is a programming model and an associated implementation for processing and generating large data sets. 
Users specify a map function that processes a key/value pair to generate a set of intermediate key/value pairs, 
and a reduce function that merges all intermediate values associated with the same intermediate key. 
Many real world tasks are expressible in this model, as shown in the paper.
#####
MapReduce是一个关于实施大型数据集处理和生成的编程模型。
用户指定一个用于处理**k/v对**并生成**中间态k/v对集合**的映射（map）函数，以及一个用于合并所有具有相同中间态key的中间态value值的归约（reduce）函数。
很多现实世界中的任务都可以通过该模型（MapReduce）表达，后续的论文中将会展示这一点。

#####
Programs written in this functional style are automatically parallelized and executed on a large cluster of commodity machines. 
The run-time system takes care of the details of partitioning the input data, 
scheduling the program’s execution across a set of machines, handling machine failures, 
and managing the required inter-machine communication. 
This allows programmers without any experience with parallel and distributed systems 
to easily utilize the resources of a large distributed system.
#####
以这种函数式风格编写的程序可以在一个大型的商用机器集群中自动、并行的执行。
该系统在运行时会关注如下细节：输入数据的分割，在一系列机器间跨机器的调度程序的执行，机器故障的处理以及管理集群内机器间的必要通信。
这（使用MapReduce）使得没有任何并行计算、分布式系统经验的程序员们都可以轻松利用大型分布式系统中的资源。

#####
Our implementation of MapReduce runs on a large cluster of commodity machines and is highly scalable:
a typical MapReduce computation processes many terabytes of data on thousands of machines. 
Programmers find the system easy to use: hundreds of MapReduce programs have been implemented 
and upwards of one thousand MapReduce jobs are executed on Google’s clusters every day.
#####
我们已实现的MapReduce运行在一个大型商用机器集群上，而且具有高度的可拓展性：一个典型的MapReduce计算可以在数千台机器上处理TB级别的数据。
程序员们发现系统很容易使用：已经有数以百计的MapReduce程序被实现，并且每天都有一千以上的MapReduce任务运行在谷歌的（计算机）集群中。

### 1 Introduction（介绍）
#####
Over the past five years, the authors and many others at Google have implemented hundreds of special-purpose computations 
that process large amounts of raw data, such as crawled documents, web request logs, etc.
, to compute various kinds of derived data, such as inverted indices, various representations of the graph structure of web documents, 
summaries of the number of pages crawled per host, the set of most frequent queries in a given day, etc. 
Most such computations are conceptually straightforward. 
However, the input data is usually large and the computations have to be distributed across hundreds or thousands of machines in order to finish in a reasonable amount of time. 
The issues of how to parallelize the computation, distribute the data, 
and handle failures conspire to obscure the original simple computation with large amounts of complex code to deal with these issues.
#####
在过去的五年时间里，包括作者在内的许多谷歌工作人员实现了数以百计的、用于特殊目的的计算程序来处理大量的原始数据，例如爬虫获取到的文档、网络请求日志等等。
其目的是为了计算出各种类型的衍生数据，例如倒排索引、多种关于web文档的图结构表示、被每个主机所爬取的页面数摘要、给定的某天中被最频繁查询的集合等等。
大多数这样的计算在概念上都很简单，然而输入的数据却通常是巨大的。而且为了能在一个合理的时间范围内完成，计算操作需要被分配到数百甚至数千台机器上运行。
关于如何并行计算，如何分派数据以及如何处理故障等问题被混杂在了一起，使得原本简单的计算逻辑被用于处理这些问题的大量复杂代码所模糊。

#####
As a reaction to this complexity, we designed a new abstraction that allows us to express the simple computations 
we were trying to perform but hides the messy details of parallelization, fault-tolerance, data distribution and load balancing in a library. 
Our abstraction is inspired by the map and reduce primitives present in Lisp and many other functional languages. 
We realized that most of our computations involved applying a map operation to each logical “record” in our input in order to compute a set of intermediate key/value pairs, 
and then applying a reduce operation to all the values that shared the same key, in order to combine the derived data appropriately. 
Our use of a functional model with user specified map and reduce operations allows us to parallelize large computations easily
and to use re-execution as the primary mechanism for fault tolerance.
#####
为了应对这些复杂性，我们设计了一个全新的抽象，该抽象允许我们表达我们想要执行的简单计算，但是将关于并行化、容错、数据分发和负载均衡等机制中复杂、繁琐的细节隐藏在了库中。
我们的这一抽象其设计灵感是来源于Lisp和很多其它函数式语言中的map和reduce原语。
我们意识到我们的绝大多数计算都涉及到为每一个输入的逻辑记录应用(applying)一个map映射操作，目的是对输入集计算从而将其转化为一个中间态的k/v对集合；然后再对所有拥有相同key值的k/v对中的value值应用一个reduce规约操作，目的是恰当地合并衍生数据。 
通过一个由用户指定具体逻辑的map和reduce操作的函数式模型，使得我们能轻易地并行化大规模的计算，并且将重复执行（自动重试）机制作为容错的主要手段。

#####
The major contributions of this work are a simple and powerful interface that enables automatic parallelization 
and distribution of large-scale computations, 
combined with an implementation of this interface that achieves high performance on large clusters of commodity PCs.
#####
这项工作的主要贡献在于提供了一个简单且强大的接口，该接口能够使大规模计算自动地并行化和分布式执行。
结合该接口的实现，已实现在大型的商用PC集群中获得高性能。

#####
Section 2 describes the basic programming model and gives several examples. 
Section 3 describes an implementation of the MapReduce interface tailored towards our cluster-based computing environment. 
Section 4 describes several refinements of the programming model that we have found useful. 
Section 5 has performance measurements of our implementation for a variety of tasks. 
Section 6 explores the use of MapReduce within Google including our experiences in using it 
as the basis for a rewrite of our production indexing system. 
Section 7 discusses related and future work.
#####
第二个章节描述了基本的编程模型并且给出了几个示例。
第三个章节描述了一个针对基于集群计算环境的MapReduce接口实现。
第四个章节描述了几个我们发现的，关于该编程模型的有效改进。
第五个章节则是关于我们对各式各样任务所实施的性能测量。
第六个章节探讨了MapReduce在谷歌内部的应用，其中包括了我们以MapReduce为基础去重建生产环境索引系统的经验。
第七个章节讨论了一些相关的话题以及日后要做的工作。

### 2 Programming Model（编程模型）
#####
The computation takes a set of input key/value pairs, and produces a set of output key/value pairs. 
The user of the MapReduce library expresses the computation as two functions: Map and Reduce.
#####
该计算获得并输入一个k/v键值对集合，然后生成并输出一个k/v键值对集合。
MapReduce库的用户通过Map和Reduce这两个函数来表达计算逻辑。

#####
Map, written by the user, takes an input pair and produces a set of intermediate key/value pairs. 
The MapReduce library groups together all intermediate values associated with the same intermediate key _I_ 
and passes them to the Reduce function.
#####
Map函数是由用户编写的，获得一个输入的k/v对并且生成一个中间态的k/v对。
MapReduce库对所有的k/v对进行分组，使得所有有着相同中间态key值的k/v对的value值组合在一起，然后将它们传递给Reduce函数。

#####
The Reduce function, also written by the user, accepts an intermediate key _I_ and a set of values for that key. 
It merges together these values to form a possibly smaller set of values. 
Typically just zero or one output value is produced per Reduce invocation. 
The intermediate values are supplied to the user’s reduce function via an iterator. 
This allows us to handle lists of values that are too large to fit in memory.
#####
Reduce函数也是由用户编写的，其接收一个中间态的key值和与该键对应的一组value值的集合。 它会将这些value值进行统一的合并以形成一个可能更小的value值集合。
通常，每次reduce调用只会生成零个或一个输出值。这个中间态的value集合通过一个迭代器提供给用户的reduce函数。
这允许我们得以处理那些无法被完整放入内存的，过大的list集合。

###2.1 Example（示例）
#####
Consider the problem of counting the number of occurrences of each word in a large collection of documents. 
The user would write code similar to the following pseudo-code:
#####
思考一个关于再一个大型文档集合中计算每一个单词出现次数的程序。
用户将写下形如以下伪代码的代码：
```
map(String key, String value):
    // key: document name
    // value: document contents
    for each word w in value:
        EmitIntermediate(w,"1");
reduce(String key, Iterator values):
    // key: a word
    // values: a list of counts
    int result = 0;
    for each v in values:
        result += ParseInt(v);
    Emit(AsString(result));
```

#####
The map function emits each word plus an associated count of occurrences (just ‘1’ in this simple example).
The reduce function sums together all counts emitted for a particular word.
#####
这个map映射函数发出每一个单词，并附加上其出现的次数（在这个简单的例子中恰好是1）。
这个reduce规约函数则累加统计每一个发出的特定单词所有的出现计数。

#####
In addition, the user writes code to fill in a mapreduce specification object with the names of the input and output files, and optional tuning parameters. 
The user then invokes the MapReduce function, passing it the specification object. 
The user’s code is linked together with the MapReduce library (implemented in C++). 
Appendix A contains the full program text for this example.
#####
此外，用户编写代码以指定的输入、输出文件的名字和可选的调优参数来填充一个规范的mapreduce对象。
用户然后调用MapReduce函数，传递这个符合规范的对象。
用户的代码与MapReduce库（c++实现）进行链接。
附录A包含了本示例的完整程序文本。

###2.2 Types（类型）
#####
Even though the previous pseudo-code is written in terms of string inputs and outputs, 
conceptually the map and reduce functions supplied by the user have associated types:
#####
尽管前面的伪代码是依据字符串类型的输入、输出编写的，
从概念上说，用户提供的map和reduce函数在类型上是有关联的：
```
map (k1,v1) --> list(k2,v2)
reduce (k2,list(v2)) --> list (v2)
```

#####
I.e., the input keys and values are drawn from a different domain than the output keys and values. 
Furthermore, the intermediate keys and values are from the same domain as the output keys and values.
#####
举个例子，输入的key和value和输出的key和value取自不同的域。
此外，中间态的key和value和输出的key和value则来自相同的域。

#####
Our C++ implementation passes strings to and from the user-defined functions 
and leaves it to the user code to convert between strings and appropriate types.
#####
我们在c++实现中传递字符串，作为用户自定义函数的输入和输出，并将其留给用户代码在字符串（类型）与合适的类型间进行转化。

### 2.3 More Examples（更多的例子）
#####
Here are a few simple examples of interesting programs that can be easily expressed as MapReduce computations.
#####
这里有几个很容易用MapReduce计算来表达的有趣程序的简单示例。

#####
**Distributed Grep:** The map function emits a line if it matches a supplied pattern. 
The reduce function is an identity function that just copies the supplied intermediate data to the output.
#####
**分布式Grep:**  
map函数输出匹配某个给定规则的一行。  
reduce函数是一个恒等函数，其只是将所输入的中间数据复制到输出（恒等函数：f(x) = x, 即输入=输出）。

#####
**Count of URL Access Frequency:** The map function processes logs of web page requests and outputs <URL,1>.
The reduce function adds together all values for the same URL and emits a <URL,total count> pair.
#####
**URL访问频率计数:**  
map函数处理网页请求的处理日志，并且输出<URL,1（访问数为1）>的键值对。  
reduce函数累加所有具有相同URL键值对的value值，并且输出一个<URL,总访问数>的键值对。

#####
**Reverse Web-Link Graph:** The map function outputs (target,source) pairs for each link to a target URL found in a page named source. 
The reduce function concatenates the list of all source URLs associated with a given target URL and emits the pair:(target,_list_(source))
#####
**反向网络链接图:**   
map函数从每一个源页面（source）中找出每一个目标页URL（target）的链接，输出（target，source）格式的kv对。  
reduce函数将所有具有相同target目标页的所有源页面（source）连接在一起组成一个列表，输出这样一个kv对（target,_list_(source)）。

#####
**Term-Vector per Host:** A term vector summarizes the most important words that occur in a document 
or a set of documents as a list of <word,frequency> pairs. 
The map function emits a <hostname,>pair for each input document (where the hostname is extracted from the URL of the document).
The reduce function is passed all per-document term vectors for a given host. 
It adds these term vectors together, throwing away infrequent terms, and then emits a final<hostname,term vector> pair.
#####
**每台主机的检索词向量**:  
汇总从一个或一系列文档中出现的最重要单词作为检索词向量（term-vector），生成以<word(单词),frequency(出现频次)>格式的kv对列表。  
map函数针对每一个输入的文档，输出一个<hostname(主机名),term vector(检索词向量)>的kv对（主机名是从文档的URL中提取出来的）。  
reduce函数接收一个给定host下所有的、基于每个文档的term-vectors(检索词向量)。将这些检索词向量进行累加，抛弃掉一些出现频率较低的检索词项，
然后返回最终的<hostname(主机名),term vector(检索词向量)>的kv对。

#####
**Inverted Index:** The map function parses each document, and emits a sequence of <word,document ID>pairs. 
The reduce function accepts all pairs for a given word, sorts the corresponding document IDs and emits a <word,_list_(document ID)> pair. 
The set of all output pairs forms a simple inverted index. 
It is easy to augment this computation to keep track of word positions.
#####
**倒排索引:**  
map函数解析每一个文档，然后输出一连串<word(单词)，documentID(文档ID)>格式的kv对。  
reduce函数接收一个给定单词对应的所有kv对，针对文档ID进行排序然后返回一个<word(单词),_list_documentID(文档ID列表)>格式的kv对。  
所有输出的kv对集合构成了一个简单的倒排索引。基于此，我们能简单的增加这种计算来追踪每一个单词(在这些文档中)的位置。

#####
Distributed Sort: The map function extracts the key from each record, and emits a <key,record> pair. 
The reduce function emits all pairs unchanged. 
This computation depends on the partitioning facilities described in Section 4.1 and the ordering properties described in Section 4.2.
#####
**分布式排序:**
map函数提取每一个记录中的key值，然后返回一个<key,record>格式的kv对。
reduce函数对所有的kv对不做修改直接返回。  
该计算依赖于后续4.1章节中所述的分区机制和4.2章节中所述的排序属性。

### 3 Implementation(实现)
#####
Many different implementations of the MapReduce interface are possible. 
The right choice depends on the environment. 
For example, one implementation may be suitable for a small shared-memory machine, 
another for a large NUMA multi-processor, and yet another for an even larger collection of networked machines.
#####
MapReduce接口可以有很多种不同的实现方式。如何进行正确的选择取决于环境。  
举个例子，某一种实现方式可能适合拥有较小共享内存的机器，而另一种实现方式则适用于大型的NUMA架构的多核处理器机器，还有的实现方式则更适用于基于网络的大型机器集群。

#####
This section describes an implementation targeted to the computing environment in wide use at Google:
large clusters of commodity PCs connected together with switched Ethernet.
In our environment:
(1) Machines are typically dual-processor x86 processors running Linux, with 2-4 GB of memory per machine.
(2) Commodity networking hardware is used – typically either 100 megabits/second or 1 gigabit/second at the machine level, 
but averaging considerably less in over-all bisection bandwidth.
(3) A cluster consists of hundreds or thousands of machines, and therefore machine failures are common.
(4) Storage is provided by inexpensive IDE disks attached directly to individual machines. 
A distributed file system developed in-house is used to manage the data stored on these disks. 
The file system uses replication to provide availability and reliability on top of unreliable hardware.
(5) Users submit jobs to a scheduling system. Each job consists of a set of tasks,
and is mapped by the scheduler to a set of available machines within a cluster.
#####
本章介绍一个针对谷歌内部广泛使用的计算环境下的(MapReduce)实现：通过交换式以太网互相连接起来的大型商用PC集群。  
在我们的环境中:  
(1) 机器通常是运行linux操作系统的、x86架构的双处理器的平台，每台机器有2-4GB的内存。  
(2) 使用商用的网络硬件 - 通常每台机器的带宽为100M/s或者1GB/s，但其平均(实际使用的)带宽远小于整个网络带宽的一半。  
(3) 一个集群由几百或几千的机器组成，因此机器故障是常见的。  
(4) 存储是由直接连接到独立机器上的IDE硬盘提供的。存储在这些磁盘上的数据由一个内部自研的分布式文件系统来管理。
这一文件系统采用复制机制，在不可靠的硬件之上实现可用性和可靠性。
(5) 用户提交作业(job)给一个调度系统。每一个作业都由一系列的任务(task)组成，且任务由调度器映射(调度)到内部集群中的一组可用机器上执行。

### 3.1 Execution Overview(执行概述)
#####
The Map invocations are distributed across multiple machines by automatically partitioning the input data into a set of M splits. 
The input splits can be processed in parallel by different machines. 
Reduce invocations are distributed by partitioning the intermediate key space into R pieces using a partitioning function (e.g., hash(key) mod R). 
The number of partitions (R) and the partitioning function are specified by the user.
#####
通过将输入的数据自动分割为M份，map调用得以分布在多个机器上调用执行。  
拆分后的输入数据可以被不同的机器并行的处理。  
通过一个分区函数将中间态的key值空间划分为R份(例如: hash(key) mod R, 对key做hash后再对R求模)，Reduce调用也得以分布式的执行。  
划分的个数(R)和分区函数都由用户来指定。

![执行概述.png](Execution_Overview.png)

#####
Figure 1 shows the overall flow of a MapReduce operation in our implementation. 
When the user program calls the MapReduce function, the following sequence of actions occurs 
(the numbered labels in Figure 1 correspond to the numbers in the list below):
#####
图1展示了我们所实现的MapReduce操作中的总体流程。  
当用户程序调用MapReduce函数时，会发生以下的一系列动作（图1中的数字标号与以下列表中的数字是一一对应的）

#####
1. The MapReduce library in the user program first splits the input files into M pieces of typically 16 megabytes to 64 megabytes (MB) per piece
(controllable by the user via an optional parameter). 
It then starts up many copies of the program on a cluster of machines.
#####
1. 内嵌于用户程序中的MapReduce库首先将输入的文件拆分为M份，每份通常为16MB至64MB大小（具体的大小可以由用户通过可选参数来控制）。  
然后，便在集群的一组机器上启动多个程序的副本。

#####
2. One of the copies of the program is special – the master. The rest are workers that are assigned work by the master.
There are M map tasks and R reduce tasks to assign. The master picks idle workers and assigns each one a map task or a reduce task.
#####
2.
其中一个程序的副本是特殊的-即master(主人)。剩下的程序副本都是worker(工作者),worker由master来分配任务。  
这里有M个map任务和R个reduce任务需要分配。master选择空闲的worker，并且为每一个被选中的worker分配一个map任务或一个reduce任务。

#####
3.
A worker who is assigned a map task reads the contents of the corresponding input split. 
It parses key/value pairs out of the input data and passes each pair to the user-defined Map function. 
The intermediate key/value pairs produced by the Map function are buffered in memory.
#####
一个被分配了map任务的worker，读取被拆分后的对应输入内容。  
从输入的数据中解析出key/value键值对，并将每一个kv作为参数传递给用户自定义的map函数。
map函数会产生中间态的key/value键值对，并被缓存在内存之中。