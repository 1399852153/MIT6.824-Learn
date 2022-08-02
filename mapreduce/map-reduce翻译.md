# MapReduce: Simplified Data Processing on Large Clusters（MapReduce: 简化大型集群下的数据处理）

##### 作者：Jeffrey Dean and Sanjay Ghemawat

### Abstract（摘要）
######
MapReduce is a programming model and an associ-
ated implementation for processing and generating large
data sets. Users specify a map function that processes a
key/valuepair to generate a set of intermediate key/value
pairs, and a reduce function that merges all intermediate
values associated with the same intermediate key. Many
real world tasks are expressible in this model, as shown
in the paper.
#####
MapReduce是一个关于实施大型数据集处理和生成的程序模型。
用户指定一个用于处理**key/value对**并生成**中间态key/value对集合**的映射（map）函数，以及一个用于合并所有具有相同中间态key的中间态value值的归约（reduce）函数。
现实世界中的很多任务都可以通过该模型（MapReduce）表达，后续的论文中将会展示这一点。

#####
Programs written in this functional style are automati-
cally parallelized and executed on a large cluster of com-
modity machines. The run-time system takes care of the
details of partitioning the input data, scheduling the pro-
gram’s execution across a set of machines, handling ma-
chine failures, and managing the required inter-machine
communication. This allows programmers without any
experience with parallel and distributed systems to eas-
ily utilize the resources of a large distributed system.
#####
以这种函数式风格编写的程序可以在一个大型的商用机器集群中自动、并行的执行。
这个系统在运行时会关注如下细节：输入数据的分割，在一系列机器间跨机器的调度程序的执行，机器故障的处理以及管理集群内机器间的必要通信。
这（使用MapReduce）使得没有任何并行计算、分布式系统经验的程序员们都可以轻松利用大型分布式系统中的资源。
