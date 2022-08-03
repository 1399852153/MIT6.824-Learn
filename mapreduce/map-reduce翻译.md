# MapReduce: Simplified Data Processing on Large Clusters（MapReduce: 简化大型集群下的数据处理）

##### 作者：Jeffrey Dean and Sanjay Ghemawat

### Abstract（摘要）
######
MapReduce is a programming model and an associated implementation for processing and generating large data sets. 
Users specify a map function that processes a key/value pair to generate a set of intermediate key/value pairs, 
and a reduce function that merges all intermediate values associated with the same intermediate key. 
Many real world tasks are expressible in this model, as shown in the paper.
#####
MapReduce是一个关于实施大型数据集处理和生成的程序模型。
用户指定一个用于处理**k/v对**并生成**中间态k/v对集合**的映射（map）函数，以及一个用于合并所有具有相同中间态key的中间态value值的归约（reduce）函数。
现实世界中的很多任务都可以通过该模型（MapReduce）表达，后续的论文中将会展示这一点。

#####
Programs written in this functional style are automatically parallelized and executed on a large cluster of commodity machines. 
The run-time system takes care of the details of partitioning the input data, 
scheduling the program’s execution across a set of machines, handling machine failures, 
and managing the required inter-machine communication. 
This allows programmers without any experience with parallel and distributed systems 
to easily utilize the resources of a large distributed system.
#####
以这种函数式风格编写的程序可以在一个大型的商用机器集群中自动、并行的执行。
这个系统在运行时会关注如下细节：输入数据的分割，在一系列机器间跨机器的调度程序的执行，机器故障的处理以及管理集群内机器间的必要通信。
这（使用MapReduce）使得没有任何并行计算、分布式系统经验的程序员们都可以轻松利用大型分布式系统中的资源。

#####
Our implementation of MapReduce runs on a large cluster of commodity machines and is highly scalable:
a typical MapReduce computation processes many terabytes of data on thousands of machines. 
Programmers find the system easy to use: hundreds of MapReduce programs have been implemented 
and upwards of one thousand MapReduce jobs are executed on Google’s clusters every day.
#####
我们实现的MapReduce运行在一个大型商用机器集群上，而且具有高度的可拓展性：一个典型的MapReduce计算可以在数千台机器上处理TB级别的数据。  
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
在过去的五年时间里，包括作者在内的许多谷歌工作人员实现了数以百计的、用于特殊目的的计算(程序)来处理大量的原始数据，例如爬虫获取到的文档、网络请求日志等等。

