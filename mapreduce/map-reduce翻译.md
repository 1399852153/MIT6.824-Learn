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
我们意识到我们的绝大多数计算都涉及到为每一个输入的逻辑记录应用(applying)一个map映射操作，目的是对输入集计算从而将其转化为一个中间态的k/v对集合；
然后为了恰当地合并衍生数据，再对所有拥有相同key值的k/v对中的value值应用一个reduce规约操作。 
通过一个由用户指定具体逻辑的map和reduce操作的函数式模型，使得我们能轻易地并行化大规模的计算，并且将重复执行（自动重试）机制作为容错的主要手段。

#####
The major contributions of this work are a simple and powerful interface that enables automatic parallelization 
and distribution of large-scale computations, 
combined with an implementation of this interface that achieves high performance on large clusters of commodity PCs.
#####
这项工作的主要贡献在于提供了一个简单且强大的接口，该接口能够使大规模计算自动地并行化和分布式的执行。
结合该接口的实现，从而在大型的商用PC集群中获得高性能。

#####
Section 2 describes the basic programming model and gives several examples. 
Section 3 describes an implementation of the MapReduce interface tailored towards our cluster-based computing environment. 
Section 4 describes several refinements of the programming model that we have found useful. 
Section 5 has performance measurements of our implementation for a variety of tasks. 
Section 6 explores the use of MapReduce within Google including our experiences in using it 
as the basis for a rewrite of our production indexing system. 
Section 7 discusses related and future work.
#####
第二章介绍了基本的编程模型并给出了几个示例。
第三章介绍了一个针对集群计算环境的MapReduce接口实现。
第四章介绍了几个我们发现的，关于该编程模型的有效改进。
第五章则是关于我们对各式各样任务所实施的性能测量。
第六章探讨了MapReduce在谷歌内部的应用，其中包括了我们以MapReduce为基础去重建生产环境索引系统的经验。
第七章讨论了一些相关的话题以及日后要做的工作。

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

1. The MapReduce library in the user program first splits the input files into M pieces of typically 16 megabytes to 64 megabytes (MB) per piece
   (controllable by the user via an optional parameter).
   It then starts up many copies of the program on a cluster of machines.

2. One of the copies of the program is special – the master. The rest are workers that are assigned work by the master.
   There are M map tasks and R reduce tasks to assign. The master picks idle workers and assigns each one a map task or a reduce task.

3. A worker who is assigned a map task reads the contents of the corresponding input split.
   It parses key/value pairs out of the input data and passes each pair to the user-defined Map function.
   The intermediate key/value pairs produced by the Map function are buffered in memory.

4. Periodically, the buffered pairs are written to local disk, partitioned into R regions by the partitioning function.
   The locations of these buffered pairs on the local disk are passed back to the master,
   who is responsible for forwarding these locations to the reduce workers.

5. When a reduce worker is notified by the master about these locations,
   it uses remote procedure calls to read the buffered data from the local disks of the map workers.
   When a reduce worker has read all intermediate data, it sorts it by the intermediate keys so
   that all occurrences of the same key are grouped together.
   The sorting is needed because typically many different keys map to the same reduce task.
   If the amount of intermediate data is too large to fit in memory, an external sort is used.

6. The reduce worker iterates over the sorted intermediate data and for each unique intermediate key encountered,
   it passes the key and the corresponding set of intermediate values to the user’s Reduce function.
   The output of the Reduce function is appended to a final output file for this reduce partition.

7. When all map tasks and reduce tasks have been completed, the master wakes up the user program.
   At this point, the MapReduce call in the user program returns back to the user code.
#####
图1展示了我们所实现的MapReduce操作中的总体流程。当用户程序调用MapReduce函数时，会发生以下的一系列动作（图1中的数字标号与以下列表中的数字是一一对应的）:

1. 内嵌于用户程序中的MapReduce库首先会将输入的文件拆分为M份，每份大小通常为16MB至64MB（具体的大小可以由用户通过可选参数来控制）。
   随后便在集群中的一组机器上启动多个程序的副本。

2. 其中一个程序的副本是特殊的-即master(主人)。剩下的程序副本都是worker(工作者),worker由master来分配任务。
   这里有M个map任务和R个reduce任务需要分配。master选择空闲的worker，并且为每一个被选中的worker分配一个map任务或一个reduce任务。

3. 一个被分配了map任务的worker，读取被拆分后的对应输入内容。
   从输入的数据中解析出key/value键值对，并将每一个kv对作为参数传递给用户自定义的map函数。 
   map函数产生的中间态key/value键值对会被缓存在内存之中。

4. 每隔一段时间，缓存在内存中的kv对会被写入本地磁盘，并被分区函数划分为R个区域。
   这些在本地磁盘上被缓冲的kv对的位置将会被回传给master，master负责将这些位置信息转发给后续执行reduce任务的worker。

5. 当一个负责reduce任务的worker被master通知了这些位置信息(map任务生成的中间态kv对数据所在的磁盘信息)， 
   该worker通过远过程调用(RPC)从负责map任务的worker机器的本地磁盘中读取被缓存的数据。
   当一个负责reduce任务的worker已经读取了所有的中间态数据，将根据中间态kv对的key值进行排序，因此所有拥有相同key值的kv对将会被分组在一起。
   需要排序的原因是因为通常很多不同的key(的kv对集合)会被映射到同一个reduce任务中去。如果(需要排序的)中间态的数据量过大，无法完全装进内存时，将会使用外排序。
   
6. 负责reduce任务的worker迭代所有被排好序的中间态数据，并将所遇到的每一个唯一的key值和其对应的中间态value值集合传递给用户自定义的reduce函数。
   reduce函数所产生的输出将会追加在一个该reduce分区内的、最终的输出文件内。

7. 当所有的map任务和reduce任务都完成后，master将唤醒用户程序。此时，调用MapReduce的用户程序(的执行流)将会返回到用户代码中。

#####
After successful completion, the output of the mapreduce execution is available in the R output files 
(one per reduce task, with file names as specified by the user).
Typically, users do not need to combine these R output files into one file – they often pass these files as input to another MapReduce call, 
or use them from another distributed application that is able to deal with input that is partitioned into multiple files.
#####
在成功的完成后，MapReduce执行的输出结果将被存放在R个输出文件中(每一个reduce任务都对应一个输出文件，输出文件的名字由用户指定)。  
通常，用户无需将这R个输出文件合并为一个文件 - 他们通常传递这些文件，将其作为另一个MapReduce调用的输入，  
或者由另一个能处理多个被分割的输入文件的分布式应用使用。

### 3.2 Master Data Structures(Master数据结构)
#####
The master keeps several data structures. For each map task and reduce task, it stores the state (idle, in-progress, or completed), 
and the identity of the worker machine(for non-idle tasks).
#####
master中维护了一些数据结构。对于每一个map和reduce任务，master存储了对应的任务状态(闲置的，运行中，或者已完成)，以及worker机器的id(针对非空闲的任务)。

#####
The master is the conduit through which the location of intermediate file regions is propagated from map tasks to reduce tasks. 
Therefore, for each completed map task, the master stores the locations and sizes of the R intermediate file regions produced by the map task. 
Updates to this location and size information are received as map tasks are completed. 
The information is pushed incrementally to workers that have in-progress reduce tasks.
#####
master是一个管道，将中间态文件的位置信息从map任务传递给reduce任务。  
因此，对于每个已完成的map任务，master存储了由map任务生成的R个中间态文件区域的位置和大小。  
当map任务完成时，master将更新接受到的(中间态文件区域)位置和大小信息。
这些信息的变更会以增量的方式推送给运行中的reduce任务。

### 3.3 Fault Tolerance(容错)
#####
Since the MapReduce library is designed to help process very large amounts of data using hundreds or thousands of machines, 
the library must tolerate machine failures gracefully.
#####
由于MapReduce库是被设计用于在几百或几千台机器上进行大规模数据处理的，所以该库必须能优雅地处理机器故障。

##### Worker Failure(Worker故障)
#####
The master pings every worker periodically.
If no response is received from a worker in a certain amount of time, the master marks the worker as failed.
Any map tasks completed by the worker are reset back to their initial idle state,
and therefore become eligible for scheduling on other workers.
Similarly, any map task or reduce task in progress on a failed worker is also reset to idle and becomes eligible for rescheduling.
#####
master会周期性的ping每一个worker。
如果在一定的时间内没有接收到来自某一worker的响应，master将会将worker标记为有故障(failed)。  
所有由该worker完成的map任务将会被重置回初始状态，因此这些map任务能被其它worker去调度执行。
类似的，任何在这个有故障的worker上处理中的map或reduce任务状态也将被重置为初始化，并且(这些被重置的任务)能够被重新调度执行。

#####
Completed map tasks are re-executed on a failure because their output is stored on the local disk(s) of the failed machine and is therefore inaccessible. 
Completed reduce tasks do not need to be re-executed since their output is stored in a global file system.
#####
已完成的map任务在故障时需要被重复执行的原因在于map任务的输出是被存储在故障机器的本地磁盘上的，因此无法被访问到(宕机或者网络不通等情况)。  
而已完成的reduce任务不需要重复执行的原因在于其输出是被存储在全局的文件系统中的。

#####
When a map task is executed first by worker A and then later executed by worker B (because A failed), all workers executing reduce tasks are notified of the re-execution. 
Any reduce task that has not already read the data from worker A will read the data from worker B.
#####
当一个map任务在worker A上被首次执行，不久后又被worker B执行(因为worker A发生了故障)，所有执行reduce任务的worker将会被通知需要重新执行。
所有还没有从worker A处读取(完整)数据的reduce任务将改为从worker B处读取数据。

#####
MapReduce is resilient to large-scale worker failures.
For example, during one MapReduce operation, network maintenance on a running cluster was causing groups of 80 machines at a time to become unreachable for several minutes. 
The MapReduce master simply re-executed the work done by the unreachable worker machines, and continued to make forward progress,eventually completing the MapReduce operation.
#####
MapReduce能从大范围的worker故障中迅速的恢复。
例如，在一个MapReduce操作运行期间内，一个正在运行的集群上的一次网络维护导致了80台机器在几分钟内无法访问的。  
MapReduce的master只需要将这些无法访问的机器上的任务重新的执行，然后继续向前推进，最终完成这个MapReduce操作。

##### Master Failure(Master故障)
#####
It is easy to make the master write periodic checkpoints of the master data structures described above. 
If the master task dies, a new copy can be started from the last checkpointed state. 
However, given that there is only a single master, its failure is unlikely; 
therefore our current implementation aborts the MapReduce computation if the master fails. 
Clients can check for this condition and retry the MapReduce operation if they desire.
#####
可以简单的让master周期性的将上述的master数据结构以检查点的形式持久化。  
如果master任务机器宕机了，一个新的master备份机器将会从最新的检查点状态处启动。  
然而，考虑到只有一台master机器，是不太可能出现故障的；因此如果master故障了，我们当前的实现会中止MapReduce计算。
客户端可以检查master的这些状态，并根据需要重试MapReduce操作。

##### Semantics in the Presence of Failures(面对故障时的语义)
#####
When the user-supplied map and reduce operators are deterministic functions of their input values, 
our distributed implementation produces the same output as would have been produced 
by a non-faulting sequential execution of the entire program.
#####
当用户提供的map和reduce算子都是基于其输入的确定性函数时，我们所实现的分布式(计算)的输出与整个程序的一个无故障的顺序串行执行后会的输出(结果)是一样的。

#####
We rely on atomic commits of map and reduce task outputs to achieve this property. 
Each in-progress task writes its output to private temporary files. 
A reduce task produces one such file, and a map task produces R such files (one per reduce task). 
When a map task completes, the worker sends a message to the master and includes the names of the R temporary files in the message. 
If the master receives a completion message for an already completed map task, it ignores the message. 
Otherwise, it records the names of R files in a master data structure.
#####
我们依赖map和reduce任务输出结果的原子性提交机制来实现这一特性。  
每一个处理中的任务将它们的输出写入其(任务)私有的临时文件中。  
一个reduce任务产生一个这样的文件，同时一个map任务产生R个这样的文件(共R个文件，R个reduce任务每个各对应一个文件)。  
当一个map任务完成后，对应worker会发送给master一个消息，消息内包含了这R个临时文件名字的。  
如果master接受到一个(已被标记为)已完成状态任务的完成消息时，其会忽略该消息。
否则，将这R个文件的名字记录到master(维护)的数据结构中。

#####
When a reduce task completes, the reduce worker atomically renames its temporary output file to the final output file. 
If the same reduce task is executed on multiple machines, multiple rename calls will be executed for the same final output file. 
We rely on the atomic rename operation provided by the underlying file system to guarantee 
that the final file system state contains just the data produced by one execution of the reduce task.
#####
当一个reduce任务完成了，执行reduce任务的worker会原子性的将临时的输出文件重命名为最终的输出文件。  
如果在多台机器上有相同的reduce任务被执行，在同一个最终输出文件上将会被执行多次重命名调用。  
我们依赖底层文件系统所提供的原子性重命名操作来保证最终文件系统中恰好只保存了一次reduce任务执行的数据。

#####
The vast majority of our map and reduce operators are deterministic, 
and the fact that our semantics are equivalent to a sequential execution in this case makes 
it very easy for programmers to reason about their program’s behavior.
When the map and/or reduce operators are non-deterministic, we provide weaker but still reasonable semantics. 

In the presence of non-deterministic operators, the output of a particular reduce task R1 is equivalent to the output 
for R1 produced by a sequential execution of the non-deterministic program. 
However, the output for a different reduce task R2 may correspond to the output for R2 produced 
by a different sequential execution of the non-deterministic program.
#####
我们绝大多数的map和reduce算子都是确定性的(即：输出完全由输入决定，同样地输入一定有着同样地输出)，
在这种情况下我们(分布式架构下并行执行)的语义等价于(单机单线程)顺序串行执行，这一事实使得程序员很容易理解他们程序的行为。
当map或reduce算子是非确定性的，我们提供了一个稍弱但依然合理的语义。  
存在非确定性算子的情况下，一个特定reduce任务R1的输出等同于R1在非确定性程序下(单机单线程)顺序串行执行的输出。  
然而，另一个与R1不同的reduce任务R2的输出将会对应于R2在一个不同的非确定程序中以顺序串行执行的输出。

#####
Consider map task M and reduce tasks R1 and R2 . 
Let e(Ri) be the execution of Ri that committed (there is exactly one such execution). 
The weaker semantics arise because e(R1) may have read the output produced by one execution of M 
and e(R2) may have read the output produced by a different execution of M.
#####
考虑下目前有一个map任务M和两个reduce任务R1和R2。 
假设e(Ri)代表Ri任务已经被提交的一次执行(恰好只执行一次)。
由于e(R1)可能在一次执行中读取M任务产生的输出，同时e(R2)可能会在另一次执行中读取M任务产生的输出，此时将会出现弱语义。

##### 
(译者小熊餐馆注：
上面这段内容比较晦涩，这里根据我举个简单的例子来帮助大家理解。  
假设有一段话：“Your name is Tom? My name is Tom, too.”，原始需求是想利用MapReduce计算统计分词后每个单词出现的次数(例子里句子很短是为了描述，实际上可以是海量的文档)。  
我们自定义的Map函数是确定性的函数算子，输入这个字符串进行Map操作后总是会返回以下9个kv对(key是单词，value是出现的次数): <Your,1>, <name,1>, <is,1>, <Tom,1>, <My,1>, <name,1>, <is,1>, <Tom,1>, <too,1>。
无论Map函数是单机单线程顺序执行，还是在集群中并行的执行，结果都是明确不变的，也就是上述的强语义的概念。  
MapReduce库会把Key相同的kv对进行分组，并将其传递给我们自定义的reduce函数，下面是分组后会传给reduce函数算子的参数：  
<Tom,list(1,1)>, <name,list(1,1)>, <is,list(1,1)>, <Your,list(1)>, <My,list(1)>, <too,list(1)>。   
在原始需求下，当map函数计算的结果不变时，无论reduce函数算子何时执行，也无论出现故障重复执行了几次，得到的结果一定和单机单线程顺序执行相同，这也是强语义。
结果：<Tom,2>, <name,2>, <is,2>, <Your,1>, <My,1>, <too,1>。 (key为单词，value为出现的次数)
而如果改变原始需求，除了累加单词总共出现的次数还要返回reduce计算时的当前机器id。  
那么此时的reduce函数就属于不确定的函数算子了，因为即使输入相同，但每一次的执行获得的结果都不一定相等（调度到不同机器上执行，机器id不同，输出的结果也就不同）。
假设有两台reduce任务worker，id分别为aaa和bbb。  
id为aaa的worker机器上reduce任务的执行结果就是<Tom,2-aaa>, <name,2-aaa>, <is,2-aaa>, <Your,1-aaa>, <My,1-aaa>, <too,1-aaa>,是为结果result_aaa。  
id为bbb的worker机器上reduce任务的执行结果则是<Tom,2-bbb>, <name,2-bbb>, <is,2-bbb>, <Your,1-bbb>, <My,1-bbb>, <too,1-bbb>,是为结果result_bbb。  
上述的弱语义表示，无论出现了什么机器故障，虽然无法准确的得知结果到底是哪一个，但最终结果不是result_aaa就是result_bbb，反正一定是某一个reduce任务生成的完整输出数据，而绝不可能出现跨任务的数据重复、冗余、缺失等问题。
)

##### 3.4 Locality(局部性)
##### 
Network bandwidth is a relatively scarce resource in our computing environment. 
We conserve network bandwidth by taking advantage of the fact that the input data(managed by GFS) is stored on the local disks of the machines that make up our cluster. 
GFS divides each file into 64 MB blocks, and stores several copies of each block (typically 3 copies) on different machines. 
The MapReduce master takes the location information of the input files into account and attempts to schedule a map task on a machine that contains a replica of the corresponding input data. 
Failing that, it attempts to schedule a map task near a replica of that task’s input data (e.g., on a worker machine that is on the same network switch as the machine containing the data). 
When running large MapReduce operations on a significant fraction of the workers in a cluster, most input data is read locally and consumes no network bandwidth.
#####
在我们的计算环境中，网络带宽是一个相对稀缺的资源。  
我们利用输入的数据(被GFS管理)被存储在组成我们集群的机器的本地磁盘上这一事实来节约网络带宽。    
GFS将每个文件分割为64MB的块，同时为每一个块存储几个备份(通常是3个副本)在不同的机器上。  
MapReduce的master调度map任务时将输入文件的位置信息考虑在内，尽量在包含对应输入数据副本的机器上调度执行一个map任务。  
如果任务失败了，调度map任务时会让执行任务的机器尽量靠近任务所需输入数据所在的机器(举个例子，被选中的worker机器与包含数据的机器位于同一网络交换机下)。  
当集群中的相当一部分worker都在执行大型MapReduce操作时，绝大多数的输入数据都在本地读取从而不会消耗网络带宽。

##### 3.5 Task Granularity(任务粒度)
#####
We subdivide the map phase into M pieces and the reduce phase into R pieces, as described above. 
Ideally,M and R should be much larger than the number of worker machines. 
Having each worker perform many different tasks improves dynamic load balancing, and also speeds up recovery when a worker fails: 
the many map tasks it has completed can be spread out across all the other worker machines.
#####
如上所处，我们将map阶段的任务拆分为M份，同时将reduce阶段的任务拆分为R份。  
理想情况下，M和R的值都应该远大于worker机器的数量。  
让每一个worker执行很多不同的任务可以提高动态负载均衡的效率，
同时也能加快当一个worker故障时的恢复速度：（故障worker机器上）很多已经完成的map任务可以分散到所有其它的worker机器上去(重新执行)。

#####
There are practical bounds on how large M and R can be in our implementation, 
since the master must make O(M + R) scheduling decisions and keeps O(M ∗ R)state in memory as described above. 
(The constant factors for memory usage are small however: 
the O(M ∗R) piece of the state consists of approximately one byte of data per map task/reduce task pair.)
#####
在我们的实现中，对M和R的实际大小做了限制，因为master必须O(M+R)的调度决定，同时要保持O(M*R)个如上所处的内存状态。  
(然而这对于内存的总体使用率来说影响还是较小的：这O(M*R)份的状态里，构成每个map/reduce任务对的数据(只)占大约1字节。)

#####
Furthermore, R is often constrained by users because the output of each reduce task ends up in a separate output file. 
In practice, we tend to choose M so that each individual task is roughly 16 MB to 64 MB of input data
(so that the locality optimization described above is most effective), 
and we make R a small multiple of the number of worker machines we expect to use. 
We often perform MapReduce computations with M = 200,000 and R = 5,000, using 2,000 worker machines.
#####
除此之外，用户通常会限制R的大小，因为每一个reduce任务的输出最后都会在一个被拆分的输出文件中。  
实际上，我们倾向于设置M的大小使得每个独立任务所需的输入数据大约在16MB至64MB之间(使得上文所述的局部性优化效果最好), 同时我们设置R的大小为我们预期使用worker机器数量的小几倍。  
我们执行MapReduce计算时，通常使用2000台worker机器，并设置M的值为200000，R的值为5000。

##### 3.6 Backup Tasks(后备任务)
#####
One of the common causes that lengthens the total time taken for a MapReduce operation is a “straggler”:
a machine that takes an unusually long time to complete one of the last few map or reduce tasks in the computation.
Stragglers can arise for a whole host of reasons.
For example, a machine with a bad disk may experience frequent correctable errors that slow its read performance from 30 MB/s to 1 MB/s.
The cluster scheduling system may have scheduled other tasks on the machine,
causing it to execute the MapReduce code more slowly due to competition for CPU, memory, local disk, or network bandwidth.
A recent problem we experienced was a bug in machine initialization code that caused processor caches to be disabled:
computations on affected machines slowed down by over a factor of one hundred.
#####
导致MapReduce运算总耗时变长的一个常见的原因是存在“落伍者”：即一台机器花费了异常长的时间去完成计算中最后的几个map或reduce任务。
导致“落伍者”出现的原因多种多样。 
举个例子，一台有着坏磁盘的机器可能会在(读取磁盘时)频繁进行纠错，使得磁盘的读取性能从每秒30MB下降到每秒1MB。  
集群调度系统可能还将其它任务也调度到了这台机器上，由于CPU、内存、本地磁盘或网络带宽的竞争，使得MapReduce代码的执行变得更加的缓慢。  
我们最近遇到的一个问题是由机器初始化代码中的一个bug导致的，其禁用了处理器的缓存：受到影响的机器其计算速度(比正常情况下)慢了100倍以上。

#####
We have a general mechanism to alleviate the problem of stragglers.
When a MapReduce operation is close to completion, the master schedules backup executions of the remaining in-progress tasks. 
The task is marked as completed whenever either the primary or the backup execution completes. 
We have tuned this mechanism so that it typically increases the computational resources used by the operation by no more than a few percent.
We have found that this significantly reduces the time to complete large MapReduce operations. 
As an example, the sort program described in Section 5.3 takes 44% longer to complete when the backup task mechanism is disabled.
#####
我们有一个通用的机制来减轻“落伍者”问题带来的影响。  
当一个MapReduce运算接近完成时，master将会调度剩下的处理中的任务进行后备执行(backup executions)。  
无论是主执行完成还是后备执行完成，这些任务都会被标记为已完成。  
我们已对这个机制进行了优化，使得这一操作令所使用的计算资源增加通常不会超过几个百分点。  
我们发现这一操作明显减少了大型MapReduce操作的完成时间。  
例如，如果禁用后备任务这一机制，在5.3节中所述的排序程序将多花费44%的时间才能完成。

### 4 Refinements(改进)
#####
Although the basic functionality provided by simply writing Map and Reduce functions is sufficient for most needs, we have found a few extensions useful. 
These are described in this section.
#####
尽管已提供的编写简单Map和Reduce函数的功能能满足大多数需求，但我们还发现了一些有价值的拓展。
本章节将对此进行介绍。

##### 4.1 Partitioning Function(分区函数)
#####
The users of MapReduce specify the number of reduce tasks/output files that they desire (R). 
Data gets partitioned across these tasks using a partitioning function on the intermediate key. 
A default partitioning function is provided that uses hashing (e.g. “hash(key) mod R”).
This tends to result in fairly well-balanced partitions. 
In some cases, however, it is useful to partition data by some other function of the key. 
For example, sometimes the output keys are URLs, and we want all entries for a single host to end up in the same output file. 
To support situations like this, the user of the MapReduce library can provide a special partitioning function. 
For example, using “hash(Hostname(urlkey)) mod R” as the partitioning function causes all URLs from the same host to end up in the same output file.
#####
MapReduce用户期望能指定reduce任务/输出文件的数量。  
在这些任务中，使用一个基于中间态key的分区函数对数据进行分区。
(我们)提供了一个使用哈希取模的默认分区函数(例如：“hash(key) mod R”)。
这往往会得到一个非常均衡的分区结果。  
然而在有些情况下，使用其它的一些基于key的分区函数对数据进行分区是很有用的。  
举个例子，有时(map任务)输出的key是URL，且我们希望同一个主机上的所有条目最后都写入同一个输出文件中。  
为了支持这种场景，MapReduce库的用户可以提供一个自定义的分区函数。  
举个例子，使用“hash(Hostname(urlkey)) mod R”作为分区函数，就可以使得来自同一个主机的所有URL(条目)最终都写入同一个输出文件中。

##### 4.2 Ordering Guarantees(有序性保证)
#####
We guarantee that within a given partition, the intermediate key/value pairs are processed in increasing key order.
This ordering guarantee makes it easy to generate a sorted output file per partition,
which is useful when the output file format needs to support efficient random access lookups by key,
or users of the output find it convenient to have the data sorted.
#####
我们保证在给定的分区内，中间态的k/v对是以中间态key值递增的顺序处理的。  
这一有序性保证使得能简单的为每个分区生成一个已排序的输出文件，
当输出文件的格式需要支持基于key来进行高效随机查找时(这一机制)会很有价值,或者用户需要已经排好序的数据时会很方便。

##### 4.3 Combiner Function(组合器函数)
#####
In some cases, there is significant repetition in the intermediate keys produced by each map task, 
and the user-specified Reduce function is commutative and associative. 
A good example of this is the word counting example in Section 2.1. 
Since word frequencies tend to follow a Zipf distribution, each map task will produce hundreds or thousands of records of the form <the, 1>. 
All of these counts will be sent over the network to a single reduce task and then added together by the Reduce function to produce one number. 
We allow the user to specify an optional Combiner function that does partial merging of this data before it is sent over the network.
#####
在一些情况下，每个map任务所生成的中间态key存在明显的重复，同时用户自定义的reduce函数具备可交换性和可结合性。  
2.1章节中的单词计数的示例程序就是一个很好的例子。  
由于单词出现的频率遵循齐夫分布(Zipf distribution)，因此每一个map任务都将生成几百或几千的形如<the,1>的记录。
所有的这些计数将通过网络发送给一个单独的reduce任务，然后再通过reduce函数累加它们而生成一个数字。  
我们允许用户指定一个可选的Combiner函数,在数据通过网络发送前该函数将对数据进行不完全的合并。

#####
The Combiner function is executed on each machine that performs a map task.
Typically the same code is used to implement both the combiner and the reduce functions.
The only difference between a reduce function and a combiner function is how the MapReduce library handles the output of the function.
The output of a reduce function is written to the final output file.
The output of a combiner function is written to an intermediate file that will be sent to a reduce task.
#####
Combiner函数能在每一个执行map任务的机器上执行。  
通常情况下，combiner函数和reduce函数的代码实现是相同的。  
reduce函数和combiner函数间唯一的不同在于MapReduce是如何处理函数的输出。
一个reduce函数的输出会写入最终的输出文件中。  
而一个combiner函数的输出会被写入到一个中间态的文件中，并且将会发送给reduce任务。

#####
Partial combining significantly speeds up certain classes of MapReduce operations. 
Appendix A contains an example that uses a combiner.
#####
部分合并可以明显加快某些MapReduce操作的速度。
附录A中包含了一个使用combiner的例子。

##### 4.4 Input and Output Types(输入和输出的类型)
##### 
The MapReduce library provides support for reading input data in several different formats.
For example, “text” mode input treats each line as a key/value pair: the key is the offset in the file and the value is the contents of the line.
Another common supported format stores a sequence of key/value pairs sorted by key.
Each input type implementation knows how to split itself into meaningful ranges for processing as separate map tasks
(e.g. text mode’s range splitting ensures that range splits occur only at line boundaries).
Users can add support for a new input type by providing an implementation of a simple reader interface,
though most users just use one of a small number of predefined input types.
#####
MapReduce库为多种不同格式输入数据的读取提供了支持。  
例如。"文本"模式下将每一行的输入视为一个kv键值对：key是该行在文件中的偏移量，而value是该行的内容。  
另一种所支持的常用格式则存储基于key排序的一连串kv键值对。 
每一个输入类型的实现知道如何将输入的数据划分为有意义的区间，用以在一个独立的map任务中处理。
(举个例子，文本模式划分区间时确保了只会在每一行的边界上出现区间的划分)  
通过提供一个简单的reader接口实现，用户可以增加支持一种新的输入类型，即使大多数用户只会使用一个或少数几个预定义的输入类型。

#####
A reader does not necessarily need to provide data read from a file. 
For example, it is easy to define a reader that reads records from a database, or from data structures mapped in memory.
#####
reader不一定只能通过读取文件来提供数据。
举个例子，很容易定义一个reader去数据库中读取记录，或者从被映射在内存中的数据结构中读取数据。

#####
In a similar fashion, we support a set of output types for producing data in different formats 
and it is easy for user code to add support for new output types.
#####
类似的，我们也支持多种不同格式的输出数据，且用户代码中可以轻松地支持新增的一种新输出类型。

##### 4.5 Side-effects(副作用)
#####
In some cases, users of MapReduce have found it convenient to produce auxiliary files as additional outputs from their map and/or reduce operators. 
We rely on the application writer to make such side-effects atomic and idempotent. 
Typically the application writes to a temporary file and atomically renames this file once it has been fully generated.
#####
在某些场景下，MapReduce的用户发现从他们的map或reduce操作中生成辅助文件作为额外的输出可以为其带来一些便利。
我们依赖应用程序的作者(自己在程序中保证)使得这些副作用具有原子性和幂等性。  
通常，应用程序会(将额外的输出)写入一个临时文件，并且一旦完全生成该文件后便原子性的重命名这一文件。

#####
We do not provide support for atomic two-phase commits of multiple output files produced by a single task.
Therefore, tasks that produce multiple output files with cross-file consistency requirements should be deterministic. 
This restriction has never been an issue in practice.
#####
我们没有为单个任务生成多个文件的场景提供原子性二阶段提交的支持。
因此，会生成多个输出文件且具有跨文件一致性需求的任务应该是确定性的（任务是确定性函数算子）。
在我们的实践中，这一限制并没有带来什么问题。

##### 4.6 Skipping Bad Records(跳过错误的记录)
#####
Sometimes there are bugs in user code that cause the Map or Reduce functions to crash deterministically on certain records. 
Such bugs prevent a MapReduce operation from completing. 
The usual course of action is to fix the bug, but sometimes this is not feasible; perhaps the bug is in a third-party library for which source code is unavailable. 
Also, sometimes it is acceptable to ignore a few records, for example when doing statistical analysis on a large data set. 
We provide an optional mode of execution where the MapReduce library detects which records cause deterministic crashes and skips these records in order to make forward progress.
#####
又是用户的代码中存在一些bug，造成了Map或Reduce函数在处理某些数据时必定崩溃。  
这些bug会阻止MapReduce操作的完成。  
通常的做法是修复这个bug，但有时这是行不通的；可能这个bug是位于三方库中，且无法获得其源代码的。  
当然，有时忽略掉少量的数据是可以接受的，比如对一个大型数据集上进行统计分析时。  
我们提供了一个可选的执行方式，当MapReduce库检测到某些记录一定会导致崩溃时，跳过这些记录并继续向前推进。

#####
Each worker process installs a signal handler that catches segmentation violations and bus errors. 
Before invoking a user Map or Reduce operation, the MapReduce library stores the sequence number of the argument in a global variable. 
If the user code generates a signal, the signal handler sends a “last gasp” UDP packet that contains the sequence number to the MapReduce master. 
When the master has seen more than one failure on a particular record, it indicates that the record should be skipped when it issues the next re-execution of the corresponding Map or Reduce task.
#####
每个worker进程都安装了一个信号处理器，用于捕获段异常(segmentation violations)和总线错误(bus errors)。  
在调用用户的Map或Reduce操作前，MapReduce库会将参数的序列号存储在一个全局变量中。  
如果用户代码产生了一个信号，则信号处理器将会向MapReduce的master发送一个包含了(该参数)序列号的"最后喘息(last gasp)"UDP包。  
当master一个特定的记录不止一次的导致故障时，master会指示对应的Map或Reduce任务在下一次重新执行时应该跳过该记录。

##### 4.7 Local Execution(本地执行)
#####
Debugging problems in Map or Reduce functions can be tricky, since the actual computation happens in a distributed system, 
often on several thousand machines, with work assignment decisions made dynamically by the master. 
To help facilitate debugging, profiling, and small-scale testing, we have developed an alternative implementation of the MapReduce library 
that sequentially executes all of the work for a MapReduce operation on the local machine. 
Controls are provided to the user so that the computation can be limited to particular map tasks. 
Users invoke their program with a special flag and can then easily use any debugging or testing tools they find useful (e.g. gdb).
#####
在实际计算发生在分布式系统中时，调试Map或Reduce函数会变得很棘手，通常由master动态的在几千台机器上决定工作的分配。   
为了更利于调试、分析和小规模的测试，我们开发了一个(运行在本地机器上的)MapReduce库的可替代实现，该库能让所有的MapReduce工作在本地机器上顺序执行。  
控制权被交给了用户,使得计算可以被限制在指定的Map任务中。  
用户通过一个特殊的标志来调用他们的程序，然后可以轻松地使用任何他们觉得好用的调试或者测试工具(例如：gdb)。

##### 4.8 Status Information(状态信息)
#####
The master runs an internal HTTP server and exports a set of status pages for human consumption. 
The status pages show the progress of the computation, such as how many tasks have been completed, 
how many are in progress, bytes of input, bytes of intermediate data, bytes of output, processing rates, etc. 
The pages also contain links to the standard error and standard output files generated by each task. 
The user can use this data to predict how long the computation will take, and whether or not more resources should be added to the computation.
These pages can also be used to figure out when the computation is much slower than expected.
#####
master机器运行了一个内置地Http服务器，并提供了一系列地状态信息页面供用户访问。  
状态信息页面会展示计算的进度，例如有多少任务已经完成，多少任务正在执行中，输入数据的字节数，中间数据的字节数，输出数据的字节数，处理速度等等信息。  
页面也包含了指向每个任务对应的标准误差(standard error)和标准输出文件的链接。  
用户可以使用这些数据预测还要多长时间完成计算，以及是否需要为该计算投入更多资源。
这些页面也可用于找出为什么实际的计算比所预期的要慢的原因。

#####
In addition, the top-level status page shows which workers have failed, and which map and reduce tasks they were processing when they failed. 
This information is useful when attempting to diagnose bugs in the user code.
#####
此外，高级状态页展示了哪些worker机器发生了故障，以及哪些map和reduce任务在执行时发生了故障。  
在尝试调试用户代码中的bug时这些信息会很有用。

##### 4.9 Counters(计数器)
#####
The MapReduce library provides a counter facility to count occurrences of various events. 
For example, user code may want to count total number of words processed or the number of German documents indexed, etc.
#####
MapReduce库提供了一个计数器的功能，用于计数不同事件出现的次数。
例如，用户代码可能会想要统计已经处理过的单词总数或者被编入德文文档的索引数等等。

#####
To use this facility, user code creates a named counter object and then increments the counter appropriately in the Map and/or Reduce function. 
For example:
#####
为了使用这一功能，用户代码中需要创建一个名为计数器的对象，然后在Map或Reduce函数中以恰当的方式对计数器进行累加操作。
例如：
```
Count* uppercase;
uppercase = GetCounter("uppercase");

map(String name, String contents) :
    for each word w in contents:
        if(isCapitalized(w)):
            uppercase->Increment();
        EmitIntermediate(w,"1");
```

#####
The counter values from individual worker machines are periodically propagated to the master (piggybacked on the ping response). 
The master aggregates the counter values from successful map and reduce tasks and returns them to the user code when the MapReduce operation is completed.
The current counter values are also displayed on the master status page so that a human can watch the progress of the live computation. 
When aggregating counter values, the master eliminates the effects of duplicate executions of the same map or reduce task to avoid double counting. 
(Duplicate executions can arise from our use of backup tasks and from re-execution of tasks due to failures.)
#####
独立worker机器中的counter值会周期性的传递给master(在ping响应包中附带)  
master将来自已经成功完成的map和reduce任务中的counter值聚合在一起，并在MapReduce任务完成时返回给用户代码。  
当前的counter值也会展示在master的状态页上，使得用户可以看到实时的计算进度。  
在聚合counter值时，master消除了同一个map或reduce任务多次执行的影响，避免了重复计数。  
(多次执行出现的原因是我们的备份任务或任务故障时的重复执行导致的)

#####
Some counter values are automatically maintained by the MapReduce library, 
such as the number of input key/value pairs processed and the number of output key/value pairs produced.
#####
有些counter值是由MapReduce自行维护的，例如已处理的输入k/v对的数量和已生成的输出k/v对的数量。

#####
Users have found the counter facility useful for sanity checking the behavior of MapReduce operations. 
For example, in some MapReduce operations, the user code may want to ensure that the number of output pairs produced exactly equals the number of input pairs processed, 
or that the fraction of German documents processed is within some tolerable fraction of the total number of documents processed.
#####
用户发现计数器功能能很好的用于检查MapReduce操作的行为是否正常。  
例如，在某些MapReduce操作中，用户代码想要确保已生成的k/v对数量严格等于已处理的输入k/v对数量，或者确保已处理的德语文档数量在已处理的全部文档中的占比是否处于一个可接受的比例内。
