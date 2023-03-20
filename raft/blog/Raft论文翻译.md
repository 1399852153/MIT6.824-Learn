# In Search of an Understandable Consensus Algorithm(Extended Version)
寻找一种可理解的一致性算法(拓展版)

##### 作者：斯坦福大学的Diego Ongaro和John Ousterhout

## Abstract(摘要)
#####
Raft is a consensus algorithm for managing a replicated log.
It produces a result equivalent to (multi-)Paxos, and it is as efficient as Paxos, but its structure is different from Paxos; 
this makes Raft more understandable than Paxos and also provides a better foundation for building practical systems.
In order to enhance understandability, Raft separates the key elements of consensus,
such as leader election, log replication, and safety, 
and it enforces a stronger degree of coherency to reduce the number of states that must be considered.
Results from a user study demonstrate that Raft is easier for students to learn than Paxos. 
Raft also includes a new mechanism for changing the cluster membership, which uses overlapping majorities to guarantee safety.
#####
Raft是一种用于管理复制日志的一致性算法。
其和(multi-)Paxos算法作用相同，并且和Paxos一样高效，但其结构与Paxos不同；这使得Raft比起Paxos更容易理解同时也为构建实际可行的系统提供了一个更好的基础。
为了让Raft更容易理解，Raft拆分了有关一致性的关键元素，例如leader选举，日志复制以及安全性等，并通过增强一致性的程度以减少必须被考虑的状态数量。
用户的研究成果表示Raft比起Paxos要更容易让学生进行学习。
Raft还包含了一个改变集群成员的新机制，其使用重叠的大多数(overlapping majorities)来保证安全。

## 1 Introduction(介绍)
Consensus algorithms allow a collection of machines to work as a coherent group that can survive the failures of some of its members.
Because of this, they play a key role in building reliable large-scale software systems.
Paxos has dominated the discussion of consensus algorithms over the last decade: 
most implementations of consensus are based on Paxos or influenced by it, 
and Paxos has become the primary vehicle used to teach students about consensus.
#####
一致性算法允许一个机器的集群作为一个具有一致性的组来进行工作，使得在一些成员出现故障时集群依然能正常工作。
正因为如此，在构建可靠的大规模软件系统时其起到了关键的作用。
Paxos主导了过去十年中关于一致性算法的讨论：
大多数的一致性的实现都给予Paxos或者受其影响，并且Paxos成为了教导学生一致性相关知识的主要工具。

#####
Unfortunately, Paxos is quite difficult to understand, in spite of numerous attempts to make it more approachable.
Furthermore, its architecture requires complex changes to support practical systems. 
As a result, both system builders and students struggle with Paxos.
#####
不幸的是，Paxos相当的难理解，尽管很多人试图让其变得更易理解。
此外，为了支持实际的系统其架构需要进行复杂的改变。
因此，所有的系统构建者和学生都在于Paxos进行斗争。

#####
After struggling with Paxos ourselves, 
we set out to find a new consensus algorithm that could provide a better foundation for system building and education. 
Our approach was unusual in that our primary goal was understandability: 
could we define a consensus algorithm for practical systems and describe it in a way that is significantly easier to learn than Paxos?
Furthermore, we wanted the algorithm to facilitate the development of intuitions that are essential for system builders.
It was important not just for the algorithm to work, but for it to be obvious why it works.
#####
我们在与Paxos斗争后，我们开始着手去寻找一种新的一致性算法，其能够为构建系统和教育提供更好的支持。
我们的方法是不同寻常的，因为我们的主要目标是(增进)可理解性：我们可以为实际的系统定义一个一致性算法并以比Paxos更容易学习的方式去描述它吗？
此外，我们希望该算法能够促进直觉的发展，这对系统构建者来说是必要的。
重要的不仅仅是算法是如何工作的，理解算法为什么能工作也是很重要的。

#####
The result of this work is a consensus algorithm called Raft.
In designing Raft we applied specific techniques to improve understandability,
including decomposition (Raft separates leader election, log replication, and safety) 
and state space reduction (relative to Paxos, Raft reduces the degree of nondeterminism and the ways servers can be inconsistent with each other). 
A user study with 43 students at two universities shows that Raft is significantly easier to understand than Paxos: 
after learning both algorithms, 33 of these students were able to answer questions about Raft better than questions about Paxos.
#####
这项工作的成果是一个名为Raft的一致性算法。
在设计Raft时，我们应用了特别的技术来改善可理解性，包括分解(Raft将leader选举，日志复制和安全性进行了分解)
以及状态空间的缩减(相对于Paxos，Raft缩减了不确定性的程度以及服务器之间彼此不一致的方式)。
一项对两所大学中的43名学生的调查显示Raft比Paxos容易理解的多：在学习了两种算法后，相比回答Paxos相关问题，其中33名学生能更好的回答关于Raft的问题。

#####
Raft is similar in many ways to existing consensus algorithms (most notably, Oki and Liskov’s Viewstamped Replication),
but it has several novel features:
* **Strong leader:** Raft uses a stronger form of leadership than other consensus algorithms. 
  For example, log entries only flow from the leader to other servers.
  This simplifies the management of the replicated log and makes Raft easier to understand.
* **Leader election:** Raft uses randomized timers to elect leaders.
  This adds only a small amount of mechanism to the heartbeats already required for any consensus algorithm, 
  while resolving conflicts simply and rapidly.
* **Membership changes:** Raft’s mechanism for changing the set of servers in the cluster uses a new joint consensus approach
  where the majorities of two different configurations overlap during transitions.
  This allows the cluster to continue operating normally during configuration changes.

#####
Raft与已有的一致性算法在很多方面都很相似(尤其是Oki和Liskov的Viewstamped Replication算法)，但Raft有几个新颖的功能：
* **Strong leader:** Raft使用比其它一致性算法更强力的leader。
  举个例子，日志条目仅从leader流向其它服务器。这简化了被复制日志的管理并且使得Raft更加容易被理解。
* **Leader election:** Raft使用随机计时器来选举leader。
  这只在任何一致性算法都需要的心跳检测中增加了少量机制，同时简单且快速的解决冲突。
* **Membership changes:** Raft用于改变集群中服务器集合的机制使用了一种新的联合的一致性方法，其中两个不同配置的多数在过渡期间是重叠的。
  这允许集群在配置改变时继续正常工作。

#####
We believe that Raft is superior to Paxos and other consensus algorithms, both for educational purposes and as a foundation for implementation. 
It is simpler and more understandable than other algorithms;
it is described completely enough to meet the needs of a practical system; 
it has several open-source implementations and is used by several companies; 
its safety properties have been formally specified and proven; and its efficiency is comparable to other algorithms.
#####
我们认为，无论是处于教育的目的还是作为实际(系统)的实现，Raft都是胜过Paxos和其它一致性算法的。
它比其它算法更加简单和容易理解；
它被详细的描述使得其足以满足实际系统的需要；
它有着几个开源的实现并且被几家公司所使用；
它的安全性已经被正式的认定和证明；并且它的效率与其它算法相当。

#####
The remainder of the paper introduces the replicated state machine problem (Section 2), 
discusses the strengths and weaknesses of Paxos (Section 3), 
describes our general approach to understandability (Section 4),
presents the Raft consensus algorithm (Sections 5–8), 
evaluates Raft (Section 9), and discusses related work (Section 10).
#####
本文的剩余部分介绍了复制状态机问题(第2节)，
天伦了Paxos的优缺点(第3节)，
描述了我们使算法易于理解的一般性方法(第4节)，
提出了Raft一致性算法(第5-8节)，
评估了Raft(第9节)，并且讨论了相关的工作(第10节)。

## 2 Replicated state machines(复制状态机)
#####
Consensus algorithms typically arise in the context of replicated state machines.
In this approach, state machines on a collection of servers compute identical copies of the same state
and can continue operating even if some of the servers are down. 
Replicated state machines are used to solve a variety of fault tolerance problems in distributed systems.
For example, large-scale systems that have a single cluster leader, such as GFS, HDFS, and RAMCloud, 
typically use a separate replicated state machine to manage leader election and store configuration information 
that must survive leader crashes. 
Examples of replicated state machines include Chubby and ZooKeeper.

#####
一致性算法是在复制状态机的背景下产生的。
在这个方法中，服务器集合中的状态机在具有相同状态的完全一致的副本上进行计算，并且即使一些服务器已经宕机也能够持续的工作。
复制状态机被用于在分布式系统中解决一系列的容错问题。
举个例子，有着一个单独集群leader的大规模系统，例如GFS，HDFS以及RAMCloud，通常使用一个单独的复制状态机来管理leader选举和存储在leader崩溃后所必须的配置信息。
复制状态机的例子包括Chubby和ZooKeeper。

![Figure1.png](Figure1.png)

#####
Replicated state machines are typically implemented using a replicated log, as shown in Figure 1. 
Each server stores a log containing a series of commands, which its state machine executes in order. 
Each log contains the same commands in the same order, so each state machine processes the same sequence of commands.
Since the state machines are deterministic, each computes the same state and the same sequence of outputs.
#####
复制状态机通常使用复制log(replicated log)来实现，如图1所示。
每个服务器存储着一个包含一系列指令的日志，这些指令在状态机上被顺序执行。
每个日志中包含了以相同顺序排布的相同的指令，因此每个状态机都处理相同的指令序列。
因为状态机是确定性的，每一个状态机都计算出相同的状态以及有着相同的输出序列。

#####
Keeping the replicated log consistent is the job of the consensus algorithm. 
The consensus module on a server receives commands from clients and adds them to its log.
It communicates with the consensus modules on other servers to ensure that every log eventually contains 
the same requests in the same order, even if some servers fail.
Once commands are properly replicated, each server’s state machine processes them in log order, 
and the outputs are returned to clients. 
As a result, the servers appear to form a single, highly reliable state machine.
#####
保持复制日志的一致性是一致性算法的工作。
服务器中的一致性模块接受来自客户端的指令并且将其加入日志。
它与其它服务器的一致性模块进行通信以确保每一个日志最终以同样的顺序包含同样的请求，即使其中一些服务器故障了。
一旦指令被正确的复制，每一个服务器的状态机都按照日志中的顺序处理这些指令，并将输出返回给客户端。
因此，服务器的集合似乎形成了一个单独的，高度可靠的状态机。

#####
Consensus algorithms for practical systems typically have the following properties:
* They ensure safety (never returning an incorrect result) under all non-Byzantine conditions, 
  including network delays, partitions, and packet loss, duplication, and reordering.
* They are fully functional (available) as long as any majority of the servers are operational 
  and can communicate with each other and with clients.
  Thus, a typical cluster of five servers can tolerate the failure of any two servers. 
  Servers are assumed to fail by stopping; they may later recover from state on stable storage and rejoin the cluster.
* They do not depend on timing to ensure the consistency of the logs: 
  faulty clocks and extreme message delays can, at worst, cause availability problems.
* In the common case, a command can complete as soon as a majority of the cluster has responded to a single round of remote procedure calls;
  a minority of slow servers need not impact overall system performance.

#####
实际系统中的一致性算法通常具有以下属性：
* 它们确保在所有非拜占庭条件下的安全性(永远不返回错误结果)，(非拜占庭条件)包括网络延迟，分区，和丢包，重复以及重新排序。
* 只要大多数服务器能够正常工作并且能够与其它服务器以及客户端互相通信，一致性算法就能发挥其全部的功能(可用性)。
  因此，一个典型的有着5台服务器组成的集群能够容忍任意两台服务器出现故障。
  假设服务器因为故障而停机；他们可以稍后从稳定的存储状态中恢复并重新加入集群。
* 他们不依赖时间来确保日志的一致性：错误的时钟和极端的消息延迟在最坏的情况下会造成可用性问题。
* 通常情况下，只要集群中的大多数对单轮的远过程调用做出了响应，命令就可以完成。占少数的慢速服务器不会对系统整体性能造成影响。

## 3 What’s wrong with Paxos?(Paxos存在的问题)
#####
Over the last ten years, Leslie Lamport’s Paxos protocol has become almost synonymous with consensus:
it is the protocol most commonly taught in courses, and most implementations of consensus use it as a starting point.
Paxos first defines a protocol capable of reaching agreement on a single decision, such as a single replicated log entry. 
We refer to this subset as single-decree Paxos. 
Paxos then combines multiple instances of this protocol to facilitate a series of decisions such as a log (multi-Paxos).
Paxos ensures both safety and liveness, and it supports changes in cluster membership. 
Its correctness has been proven, and it is efficient in the normal case.
#####
在过去的十年中，Leslie Lamport的Paxos协议几乎已经成为了一致性算法的代名词：
它是课堂教学中最常用的协议，大多数的一致性算法也将其作为起点。
Paxos首先定义了一个协议，其能够就单个决定达成一致，例如单个日志条目的复制。
我们将这一自己称为single-decree Paxos。
然后Paxos将该协议的多个实例组合起来以达成一系列的决定，例如日志(multi-Paxos)。
Paxos同时保证了安全性和活性，并且支持集群成员的变更。
其正确性已经得到证明，并且在通常情况下是高效的。


#####
Unfortunately, Paxos has two significant drawbacks.
The first drawback is that Paxos is exceptionally difficult to understand. 
The full explanation is notoriously opaque; few people succeed in understanding it, and only with great effort.
As a result, there have been several attempts to explain Paxos in simpler terms.
These explanations focus on the single-decree subset, yet they are still challenging. 
In an informal survey of attendees at NSDI 2012, we found few people who were comfortable with Paxos, even among seasoned researchers.
We struggled with Paxos ourselves; 
we were not able to understand the complete protocol until after reading several simplified explanations 
and designing our own alternative protocol, a process that took almost a year.
#####
不幸的是，Paxos有着两个明显的缺点。
第一个缺点是Paxos异乎寻常的难理解。
Paxos出了名的难理解，即使在付出了巨大努力的情况下，也很少有人能成功的理解它。
因此，有一些人尝试着用更简单的方式来理解Paxos。
这些解释聚焦于single-decree这一子集，但这仍具有挑战性。
在一项针对NSDI 2012与会者的非正式调查中，我们发现很少有人对Paxos感到满意，即使对于经验丰富的研究员来说也是如此。
我们也与Paxos进行了艰难的斗争；直到阅读了几个简化的解释并设计了我们自己的替代方案后我们才能够理解完整的协议，而这个过程花费了将近一年的时间。

#####
We hypothesize that Paxos’ opaqueness derives from its choice of the single-decree subset as its foundation.
Single-decree Paxos is dense and subtle: 
it is divided into two stages that do not have simple intuitive explanations and cannot be understood independently. 
Because of this, it is difficult to develop intuitions about why the single-decree protocol works.
The composition rules for multi-Paxos add significant additional complexity and subtlety. 
We believe that the overall problem of reaching consensus on multiple decisions (i.e., a log instead of a single entry) 
can be decomposed in other ways that are more direct and obvious.
#####
我们猜定Paxos晦涩难懂的原因在于作者选择以single-decree这一子集作为Paxos的基础。
Single-decree Paxos是难理解和精巧的：
它被分为了两个阶段，并且没有简单直接的说明，每一阶段也无法单独的理解。
正因如此，很难凭借直觉的理解single-decree协议为什么能够工作。
multi-Paxos的组合规则也显著的增加了复杂性和微妙之处。
我们认为，就多个决定达成一致的总体问题(例如，使用日志而不是单个的entry)能够被分解为其它更直接和更容易理解的方式。

#####
The second problem with Paxos is that it does not provide a good foundation for building practical implementations. 
One reason is that there is no widely agreed-upon algorithm for multi-Paxos.
Lamport’s descriptions are mostly about single-decree Paxos; 
he sketched possible approaches to multi-Paxos, but many details are missing. 
There have been several attempts to flesh out and optimize Paxos, such as [26], [39], and [13], 
but these differ from each other and from Lamport’s sketches.
Systems such as Chubby [4] have implemented Paxos-like algorithms, but in most cases their details have not been published.
#####
Paxos的第二个问题是它没有为构建实际可行的实现提供一个好的基础。
其中一个原因是对于multi-Paxos没有一个被广泛认同的算法。
Lamport的描述大多数都是关于single-decree Paxos的；他简要的概述了实现multi-Paxos的可行的方法，但缺失了很多的细节。
已经有几个(团队)试图去具体化和优化Paxos，例如[26],[39]和[13],但这些尝试彼此间不同且也不同于Lamport的概述。
像Chubby系统已经实现了类似Paxos的算法，但大多数情况下的细节并没有被公开。

#####
Furthermore, the Paxos architecture is a poor one for building practical systems; 
this is another consequence of the single-decree decomposition.
For example, there is little benefit to choosing a collection of log entries independently and then melding them into a sequential log;
this just adds complexity. 
It is simpler and more efficient to design a system around a log, 
where new entries are appended sequentially in a constrained order. 
Another problem is that Paxos uses a symmetric peer-to-peer approach at its core
(though it eventually suggests a weak form of leadership as a performance optimization).
This makes sense in a simplified world where only one decision will be made, but few practical systems use this approach.
If a series of decisions must be made, it is simpler and faster to first elect a leader, then have the leader coordinate the decisions.
#####
此外，Paxos的架构在构建实际的系统时表现不佳；这是对single-decree进行分解的另一个结果。
例如，选择一组独立的日志集合并将其合并到一个顺序日志中几乎没有带来什么好处；这只会增加复杂性。
围绕日志来设计系统会更简单和更高效，其中新的日志条目以受约束的顺序追加。
另一个问题是，Paxos使用了一种对称的点对点(P2P)方法作为其核心(尽管最后提出了一种更弱形式的leadership作为性能优化)。
在一个只需要做一次决定的，被简化的世界中这样是行得通的，但很少有实际的系统使用这个方式。
如果有一系列的决定必须要做，首先选举出一个leader，然后leader来协调决策会更简单和更快速。

#####
As a result, practical systems bear little resemblance to Paxos. 
Each implementation begins with Paxos, discovers the difficulties in implementing it, 
and then develops a significantly different architecture. 
This is time-consuming and error-prone, and the difficulties of understanding Paxos exacerbate the problem.
Paxos’ formulation may be a good one for proving theorems about its correctness, 
but real implementations are so different from Paxos that the proofs have little value. 
#####
The following comment from the Chubby implementers is typical:  
_There are significant gaps between the description of the Paxos algorithm 
and the needs of a real-world system. . . . the final system will be based on an unproven protocol [4]._
#####
因此，实际的系统与Paxos几乎没有相似之处。
每一个实现都从Paxos出发，发现实现Paxos的困难之处，然后开发出一个与之截然不同的架构。
这既耗费时间又容易出错，并且Paxos的晦涩难懂加剧了这一问题。
Paxos的公式可能可以很好的证明其正确性，但是实际的实现与Paxos是如此的不同，以至于这些证明几乎毫无价值。
#####
以下Chubby实现者的评论是具有代表性的：  
_Paxos算法的描述与现实世界系统的需求之间有着巨大的鸿沟....最终的系统将建立在一个未被证明的协议之上。_

#####
Because of these problems, we concluded that Paxos does not provide a good foundation either for system building or for education.
Given the importance of consensus in large-scale software systems,
we decided to see if we could design an alternative consensus algorithm with better properties than Paxos. 
Raft is the result of that experiment.
#####
由于这些问题，我们的结论是Paxos并没有为构建系统或是进行教育提供一个好的基础。
考虑到一致性在大规模软件系统中的重要性，我们决定看看我们是否可以设计出一个比起Paxos有着更好特性的一致性算法。
Raft正是这一实验的成果。

## 4 Designing for understandability(为通俗易懂而设计)
#####
We had several goals in designing Raft: it must provide a complete and practical foundation for system building,
so that it significantly reduces the amount of design work required of developers; 
it must be safe under all conditions and available under typical operating conditions; and it must be efficient for common operations.
But our most important goal—and most difficult challenge—was understandability. 
It must be possible for a large audience to understand the algorithm comfortably. 
In addition, it must be possible to develop intuitions about the algorithm,
so that system builders can make the extensions that are inevitable in real-world implementations.
#####
我们在设计Raft时有几个目标：它必须为构建系统提供一个完整的和实际的基础，从而显著的减少开发者设计时所需的工作；
它必须在任何条件下都是安全的并且在典型的工作状态下是可用的；同时它必须在通常工作状态下是高效的。
但我们最重要的目标也是最困难的挑战是使得Raft通俗易懂。
必须尽可能的使大多数人能够轻松的理解该算法。
这样系统构建者才能够在现实世界的实现中进行不可避免的拓展。

#####
There were numerous points in the design of Raft where we had to choose among alternative approaches.
In these situations we evaluated the alternatives based on understandability: 
how hard is it to explain each alternative (for example, how complex is its state space,
and does it have subtle implications?), and how easy will it be for a reader to completely understand the approach and its implications?
#####
在设计Raft时有很多要点都必须在多个可选方案中抉择。
在这些情况下，我们基于易懂性来评估这些可选方案：  
对于每一个可选方案解释起来有多困难(例如，状态空间有多复杂以及是否有微妙的含义？)，以及对于一个读者来说完全理解这个方法和其含义有多容易？

#####
We recognize that there is a high degree of subjectivity in such analysis; nonetheless, we used two techniques that are generally applicable.
The first technique is the well-known approach of problem decomposition: 
wherever possible, we divided problems into separate pieces that could be solved, explained, and understood relatively independently. 
For example, in Raft we separated leader election, log replication, safety, and membership changes.
#####
我们意识到这一分析方式具有高度的主观性；尽管如此，但我们还是使用了两种可行的通用技术。
第一个技术是众所周知的问题分解方法：
在可能的情况下，我们将问题分解为几个部分，使得每一部分都可以被相对独立的解决，解释和理解。
例如，我们将Raft分解为leader选举，日志复制，安全性和成员变更这几个部分。

#####
Our second approach was to simplify the state space by reducing the number of states to consider,
making the system more coherent and eliminating nondeterminism where possible. 
Specifically, logs are not allowed to have holes, and Raft limits the ways in which logs can become inconsistent with each other.
Although in most cases we tried to eliminate nondeterminism, there are some situations where nondeterminism actually improves understandability.
In particular, randomized approaches introduce nondeterminism, 
but they tend to reduce the state space by handling all possible choices in a similar fashion(“choose any; it doesn’t matter”). 
We used randomization to simplify the Raft leader election algorithm.
#####
我们的第二种方法是通过减少需要考虑的状态数量以简化状态空间，使系统变得更加连贯并尽可能的消除不确定性。
特别的，日志是不允许存在空洞的，并且Raft限制了使得日志间变得彼此不一致的方式。
尽管在大多数情况下我们试图消除不确定性，但在一些条件下不确定性实际上能提高可理解性。
特别的，随机化方法引入了不确定性，但它们倾向于通过用相似的方式来处理所有可能的选择以减少状态空间("选择任意一个;具体是哪一个则无关紧要")。
我们使用随机化来简化Raft中的领导选举算法。

## 5. The Raft consensus algorithm(Raft一致性算法)
#####
Raft is an algorithm for managing a replicated log of the form described in Section 2. 
Figure 2 summarizes the algorithm in condensed form for reference, and Figure 3 lists key properties of the algorithm;
the elements of these figures are discussed piecewise over the rest of this section.
#####
Raft是一种管理如第二节所述的复制日志的算法。
图2以简明扼要的总结了算法以供参考，图3列举出了算法的关键特性；这些图中的元素将在本节剩余的部分中进行讨论。

#####
Raft implements consensus by first electing a distinguished leader, 
then giving the leader complete responsibility for managing the replicated log.
The leader accepts log entries from clients, 
replicates them on other servers, and tells servers when it is safe to apply log entries to their state machines. 
Having a leader simplifies the management of the replicated log. 
For example, the leader can decide where to place new entries in the log without consulting other servers,
and data flows in a simple fashion from the leader to other servers. 
A leader can fail or become disconnected from the other servers, in which case a new leader is elected.
#####
Raft通过受限选举出一位distinguished leader，然后让它全权的管理复制日志以实现一致性。
这个leader接受来自客户端的日志条目，将其复制到其它服务器中，并且在日志条目可以被安全的应用在它们的状态机上时通知这些服务器。
拥有一个leader可以简化对复制日志的管理。
例如，leader可以决定新日志条目的位置而无需咨询其它服务器，并且数据流以一种简单的形式由leader流向其它服务器。
leader可能会故障或者与其它服务器失联，这种情况下一位新的leader将会被选举出来。

#####
Given the leader approach, Raft decomposes the consensus problem into three relatively independent sub-problems, 
which are discussed in the subsections that follow:
* **Leader election:** a new leader must be chosen when an existing leader fails (Section 5.2).
* **Log replication:** the leader must accept log entries from clients and replicate them across the cluster,
  forcing the other logs to agree with its own (Section 5.3).
* **Safety:** the key safety property for Raft is the State Machine Safety Property in Figure 3: 
  if any server has applied a particular log entry to its state machine,
  then no other server may apply a different command for the same log index.
  Section 5.4 describes how Raft ensures this property; 
  the solution involves an additional restriction on the election mechanism described in Section 5.2.

After presenting the consensus algorithm, this section discusses the issue of availability and the role of timing in the system.
#####
通过引入leader的方法，Raft将一致性问题分解为3个相对独立的子问题，这些子问题将在以下子章节中被讨论：
* **leader选举：** 当一位现存的leader故障时必须选出一位新的leader(5.2节)。
* **日志复制：** leader必须从客户端接收日志条目并且在集群中复制它们，并且强制其它节点的日志与leader保持一致(5.3节)。
* **安全性：** Raft的关键安全特性就是图3中的状态机的安全特性：如果任一服务器已经将一个特定的日志条目作用于它的状态机，则没有任何服务器可以对相同的日志索引应用不同的指令。
  5.4节描述了Raft是如何确保这一特性的；这一解决方案涉及到对5.2节中所描述的选举机制的额外限制。  
#####
在展示了一致性算法后，本章节还将讨论可用性问题以及时序在系统中起到的作用。

![Figure2.png](Figure2.png)
![Figure3.png](Figure3.png)

### 5.1 Raft basics(Raft基础)
#####
A Raft cluster contains several servers; five is a typical number, which allows the system to tolerate two failures.
At any given time each server is in one of three states: leader, follower, or candidate.
In normal operation there is exactly one leader and all of the other servers are followers. 
Followers are passive: they issue no requests on their own but simply respond to requests from leaders and candidates.
The leader handles all client requests (if a client contacts a follower, the follower redirects it to the leader).
The third state, candidate, is used to elect a new leader as described in Section 5.2. 
Figure 4 shows the states and their transitions; the transitions are discussed below.

#####
Raft divides time into terms of arbitrary length, as shown in Figure 5. 
Terms are numbered with consecutive integers.
Each term begins with an election, in which one or more candidates attempt to become leader as described in Section 5.2.
If a candidate wins the election, then it serves as leader for the rest of the term. 
In some situations an election will result in a split vote.
In this case the term will end with no leader; a new term (with a new election) will begin shortly. 
Raft ensures that there is at most one leader in a given term.
#####
![Figure4.png](Figure4.png)
![Figure5.png](Figure5.png)

#####
Different servers may observe the transitions between terms at different times,
and in some situations a server may not observe an election or even entire terms. 
Terms act as a logical clock [14] in Raft, and they allow servers to detect obsolete information such as stale leaders.
Each server stores a current term number, which increases monotonically over time. 
Current terms are exchanged whenever servers communicate; 
if one server’s current term is smaller than the other’s, then it updates its current term to the larger value.
If a candidate or leader discovers that its term is out of date, it immediately reverts to follower state.
If a server receives a request with a stale term number, it rejects the request.

#####
Raft servers communicate using remote procedure calls(RPCs), and the basic consensus algorithm requires only two types of RPCs.
RequestVote RPCs are initiated by candidates during elections (Section 5.2), 
and AppendEntries RPCs are initiated by leaders to replicate log entries and to provide a form of heartbeat (Section 5.3).
Section 7 adds a third RPC for transferring snapshots between servers. 
Servers retry RPCs if they do not receive a response in a timely manner, and they issue RPCs in parallel for best performance.




