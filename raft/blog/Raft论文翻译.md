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
一个Raft的集群包含几个服务器;通常是5个节点，这样的系统能容忍系统中的2个节点出现故障。
在任一给定的时间内，每个服务器只会处于3种状态中的一种：领导者(leader),追随者(follower)，或者候选者(candidate)。
在通常情况下，只会有1个leader并且其它的服务器都是follower。
Follower都是被动的: 它们自己不会提出请求而只会简单的响应来自leader和candidate的请求。
leader处理所有来自客户端的请求(如果一个客户端与follower进行联络，follower会将其重定向到leader)。
第三种状态，candidate，用于选举出一个如5.2章节所描述的新leader。
图4展示了状态以及状态间的转换关系；转换关系将在下文被讨论。

#####
Raft divides time into terms of arbitrary length, as shown in Figure 5. 
Terms are numbered with consecutive integers.
Each term begins with an election, in which one or more candidates attempt to become leader as described in Section 5.2.
If a candidate wins the election, then it serves as leader for the rest of the term. 
In some situations an election will result in a split vote.
In this case the term will end with no leader; a new term (with a new election) will begin shortly. 
Raft ensures that there is at most one leader in a given term.
#####
Raft将时间分割为任意长度的任期(term)，如图5所示。
任期由连续的整数进行编号。
每一个任期都以一次选举开始，其中一个或更多的candidate试图成为leader(如5.2节中所描述的)。
如果一个candidate赢得了选举，然后它将在余下的任期中作为leader。
在一些情况下一次选举可能会导致分裂的投票结果。
在这种情况下，任期将在没有leader的情况下结束; 一个新的任期(伴随者一个新的选举)将很快开始。
Raft保证了在一个给定的任期内最多只会有一个leader。

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
不同服务器可能会在不同的时间上观察到任期之间的状态转换，并且在一些情况下一个服务器可能不会观察到一次选举甚至整个任期。
任期在Raft中充当逻辑时钟，并且它们允许服务器检测到过时的信息比如之前的、老leader。
每一个服务器存储了一个当前任期的编号，其随着时间单调增加。
每当服务器之间互相通信时，它们都会互相交换当前的任期(编号);如果一个服务器的当前任期(编号)小于其它的服务器，则其将会将当前的任期(编号)更新为那个更大的值。
如果一个candidate或者leader发现它们的任期(编号)已经过时，它将立即将自己恢复为follower的状态。
如果一个服务器接受到一个带有过时任期编号的请求，它将拒绝这一请求。

#####
Raft servers communicate using remote procedure calls(RPCs), and the basic consensus algorithm requires only two types of RPCs.
RequestVote RPCs are initiated by candidates during elections (Section 5.2), 
and AppendEntries RPCs are initiated by leaders to replicate log entries and to provide a form of heartbeat (Section 5.3).
Section 7 adds a third RPC for transferring snapshots between servers. 
Servers retry RPCs if they do not receive a response in a timely manner, and they issue RPCs in parallel for best performance.
#####
Raft服务器使用远过程调用(RPC)进行通信，并且基本的一致性算法只需要两种类型的RPC。
请求投票的RPC由candidate在选举期间发起(第5.2节)，拓展条目的RPC由leader发起，用于日志条目的复制以及提供心跳机制(第5.3节)。
第7节加入了第三种RPC用于在服务器间传输快照。
如果服务器在给定的时间内没有收到响应，则会对RPC进行重试，并且它们会发起并行的rpc以获得最好的性能。

### 5.2 Leader election(leader选举)
#####
Raft uses a heartbeat mechanism to trigger leader election. When servers start up, they begin as followers. 
A server remains in follower state as long as it receives valid RPCs from a leader or candidate. 
Leaders send periodic heartbeats (AppendEntries RPCs that carry no log entries) to all followers in order to maintain their authority.
If a follower receives no communication over a period of time called the election timeout, 
then it assumes there is no viable leader and begins an election to choose a new leader.
#####
Raft使用心跳机制来触发leader选举。当服务器启动时，它们会成为follower。
只要服务器能从leader或者candidate处接收到有效的RPC请求，它们就将保持follower状态。
leader向所有follower发送周期性的心跳(不携带日志条目的AppendEntries RPC)来维持它的权威性。
如果一个follower在一段被成为选举超时的时间段内未接收到任何通信，则它假设当前没有可用的leader并且发起选举来选择一个新的leader。

#####
To begin an election, a follower increments its current term and transitions to candidate state. 
It then votes for itself and issues RequestVote RPCs in parallel to each of the other servers in the cluster. 
A candidate continues in this state until one of three things happens:
(a) it wins the election, 
(b) another server establishes itself as leader, or
(c) a period of time goes by with no winner.
These outcomes are discussed separately in the paragraphs below.
#####
为了开始一轮选举，follower增加它当前的任期值并且转换为candidate状态。
然后它将选票投给它自己并且向集群中的其它服务器并行的发起请求投票的RPC(RequestVote RPCs)。
一个candidate会一直保持这种状态直到以下三种情况之一发生：
(a) 它赢得此次选举 (b) 另一个服务器将自己确认为leader，或者 (c) 一段时间后没有产生胜利者。
下文中的各个段落将分别讨论这些结果。

#####
A candidate wins an election if it receives votes from a majority of the servers in the full cluster for the same term. 
Each server will vote for at most one candidate in a given term, on a first-come-first-served basis
(note: Section 5.4 adds an additional restriction on votes). 
The majority rule ensures that at most one candidate can win the election for a particular term (the Election Safety Property in Figure 3).
Once a candidate wins an election, it becomes leader.
It then sends heartbeat messages to all of the other servers to establish its authority and prevent new elections.
#####
如果一个candidate在同一个任期内接收到了整个集群中大多数服务器的投票，其将赢得这次选举。
每个服务器在给定的某一任期内将会基于先来先服务的原则(first-come-first-served)投票给至多一位candidate(第5.4节对投票增加了额外的限制)。
多数规则确保了对于一个特定的任期，最多只会有一名candidate能够赢得选举(图3中选举的安全特性)。
一旦一个candidate赢得了一次选举，它将成为leader。
然后它向其它服务器发送心跳信息以建立权威并且阻止新的选举。

#####
While waiting for votes, a candidate may receive an AppendEntries RPC from another server claiming to be leader. 
If the leader’s term (included in its RPC) is at least as large as the candidate’s current term, 
then the candidate recognizes the leader as legitimate and returns to follower state. 
If the term in the RPC is smaller than the candidate’s current term, then the candidate rejects the RPC and continues in candidate state.
#####
在等待投票时，一个candidate可能会接受到来自自称是leader的其它服务器的AppendEntries RPC。
如果leader的任期(包含在它的RPC中)大于或等于candidate的当前任期，那么candidate承认该leader是合法的并且返回到follower状态。
如果RPC中的任期小于candidate的当前任期，candidate将会拒绝这一RPC并且继续保持candidate的状态。

#####
The third possible outcome is that a candidate neither wins nor loses the election:
if many followers become candidates at the same time, votes could be split so that no candidate obtains a majority.
When this happens, each candidate will time out and start a new election by incrementing its term 
and initiating another round of RequestVote RPCs.
However, without extra measures split votes could repeat indefinitely.
#####
第三种可能的结果是一个candidate既没有赢得选举也没有输掉选举：
如果许多follower都在同一时间成为了candidate，投票可能会被瓜分导致没有candidate获得大多数的选票。
当这种情况发生时，每一个candidate都将会超时并且通过增加它的任期值并且初始化另一轮的RequestVote RPCs以开始一轮新的选举。
然而，如果不采取额外的措施，分裂的投票可能会无限的重复。

#####
Raft uses randomized election timeouts to ensure that split votes are rare and that they are resolved quickly.
To prevent split votes in the first place, election timeouts are chosen randomly from a fixed interval (e.g., 150–300ms).
This spreads out the servers so that in most cases only a single server will time out; 
it wins the election and sends heartbeats before any other servers time out.
The same mechanism is used to handle split votes. 
Each candidate restarts its randomized election timeout at the start of an election, and it waits for that timeout to elapse before
starting the next election; this reduces the likelihood of another split vote in the new election. 
Section 9.3 shows that this approach elects a leader rapidly.
#####
Raft使用随机化的选举超时时间来确保分裂的投票很少会发生并使得它们能够被迅速的解决。
为了防止一开始就出现分裂的投票，选举的超时时间是从一个固定的间隔中被随机选取的(例如150-300ms)。
这打散了服务器使得在大多数情况下只有单独一个服务器将会超时；它赢得选举并且在其它服务器超时之前发送心跳(译者注：超时后自己就会在别的服务器没反应过来前发起新一轮任期更大的投票，让别人都投给它来赢得选举)。
同样的机制也被用于解决分裂的投票。
每个candidate在一轮选举开始时会重新随机的设置其选举超时时间，并且在下一轮选举前等待直到超时；这减少了在新的选举中再一次出现分裂投票的可能性。
第9.3节展示了该方法能迅速的选举出一个leader。

#####
![Figure6.png](Figure6.png)

#####
Elections are an example of how understandability guided our choice between design alternatives. 
Initially we planned to use a ranking system: each candidate was assigned a unique rank, which was used to select between competing candidates.
If a candidate discovered another candidate with higher rank, 
it would return to follower state so that the higher ranking candidate could more easily win the next election. 
We found that this approach created subtle issues around availability 
(a lower-ranked server might need to time out and become a candidate again if a higher-ranked server fails,
but if it does so too soon, it can reset progress towards electing a leader). 
We made adjustments to the algorithm several times, but after each adjustment new corner cases appeared.
Eventually we concluded that the randomized retry approach is more obvious and understandable.
#####
选举是一个可理解性如何指导我们在可选设计间进行选择的例子。
最初，我们计划使用等级系统(ranking system)：每一个candidate都被分配一个唯一的等级，其用于在彼此竞争的candidate做选择。
如果一个candidate发现了一个具有更高等级的candidate，它将返回到follower状态因此更好等级的candidate将更容易赢得下一次选举。
但我们发现这个方法在可用性方面存在微妙的问题(如果一个高等级的服务器故障了，则一个低等级的服务器可能需要超时并再次成为candidate，但如果这样做的太早，它将会重置选举leader的进度)。
我们对算法进行了数次调整，但每次调整后都出现了新的困境。
最终我们得出结论，随机化重试的方法更显然且更容易被理解。

### 5.3 Log replication(日志复制)
#####
Once a leader has been elected, it begins servicing client requests. 
Each client request contains a command to be executed by the replicated state machines. 
The leader appends the command to its log as a new entry, 
then issues AppendEntries RPCs in parallel to each of the other servers to replicate the entry. 
When the entry has been safely replicated (as described below), 
the leader applies the entry to its state machine and returns the result of that execution to the client. 
If followers crash or run slowly, or if network packets are lost, 
the leader retries AppendEntries RPCs indefinitely (even after it has responded to the client) 
until all followers eventually store all log entries.
#####
一旦一个leader被选举出来，它将开始服务于客户端的请求。
每一个客户端的请求都包含了一个被用于在复制状态机上执行的指令。
leader将指令作为一个新的条目追加到其日志中，然后向其它的每个服务器发起并行的AppendEntries RPC令它们复制这一条目。
当条目已被安全的被复制(如下所述)，leader在它的状态机上应用这一条目并且将执行的结果返回给客户端。
如果follower崩溃了或者运行的很慢，或者网络失包，leader会无限的重试AppendEntries RPC(即使在响应了客户端的请求之后)，
直到所有的follower最终都存储了所有的日志条目。

#####
Logs are organized as shown in Figure 6. 
Each log entry stores a state machine command along with the term number when the entry was received by the leader.
The term numbers in log entries are used to detect inconsistencies between logs and to ensure some of the properties in Figure 3. 
Each log entry also has an integer index identifying its position in the log.
#####
日志如图6所示的方式被组织。
每一个日志条目存储了一个状态机的指令，以及从leader处接受条目时的任期编号。
日志条目中的任期编号被用于检测日志间的不一致，并且用于保证图3中的一些特性。
每个日志条目也有一个整数的索引标识其在日志中的位置。

#####
The leader decides when it is safe to apply a log entry to the state machines; such an entry is called committed.
Raft guarantees that committed entries are durable and will eventually be executed by all of the available state machines.
A log entry is committed once the leader that created the entry has replicated it on a majority of the servers (e.g., entry 7 in Figure 6).
This also commits all preceding entries in the leader’s log, including entries created by previous leaders. 
Section 5.4 discusses some subtleties when applying this rule after leader changes,
and it also shows that this definition of commitment is safe. 
The leader keeps track of the highest index it knows to be committed,
and it includes that index in future AppendEntries RPCs (including heartbeats) so that the other servers eventually find out. 
Once a follower learns that a log entry is committed, it applies the entry to its local state machine (in log order).
#####
leader决定何时能安全的在状态机上应用日志条目；这样的条目被称作已提交的日志。
Raft保证已提交的条目都会被持久化并且最终将会在所有可用的状态机上被执行。
一旦被创建的条目被大多数服务器所复制，leader就会将其提交(例如，图6中的条目7)。
同时也会提交leader日志中更早之前的所有条目，其中包括被前任leader们所创建的条目。
第5.4节讨论了在领导者变更时应用这一规则的微妙之处，同时它也证明了所承诺的定义是安全的。
leader持续的跟踪它已知的被提交日志的最大索引值，并且将索引值包含在未来的AppendEntries RPC中(包括心跳)，以便其它的服务器最终能知道(最大编号的已提交索引)。
一旦一个追随者知道一个日志条目已被提交，它便将这一条目应用于本地的状态机(基于日志的顺序)。

#####
We designed the Raft log mechanism to maintain a high level of coherency between the logs on different servers.
Not only does this simplify the system’s behavior and make it more predictable, but it is an important component of ensuring safety.
Raft maintains the following properties, which together constitute the Log Matching Property in Figure 3:
* If two entries in different logs have the same index and term, then they store the same command.
* If two entries in different logs have the same index and term, then the logs are identical in all preceding entries.
#####
我们设计了Raft日志机制，其用于维持不同服务器之间日志的高度一致。
其不仅仅简化了系统的行为，还使得它更加的可预测，同时这也是确保安全性的重要部分。
Raft维护着以下特性，这些特性一并组成了图3中的日志匹配特性(Log Matching Property)：
* 如果不同日志中的两个条目有着相同的索引值和任期，则它们存储着相同的指令。
* 如果不同日志中的两个条目有着相同的索引值和任期，则该日志之前的所有条目也都是完全相同的。

#####
The first property follows from the fact that a leader creates at most one entry with a given log index in a given term, 
and log entries never change their position in the log.
The second property is guaranteed by a simple consistency check performed by AppendEntries.
When sending an AppendEntries RPC, the leader includes the index and term of the entry in its log that immediately precedes the new entries. 
If the follower does not find an entry in its log with the same index and term, then it refuses the new entries. 
The consistency check acts as an induction step: the initial empty state of the logs satisfies the Log Matching Property,
and the consistency check preserves the Log Matching Property whenever logs are extended.
As a result, whenever AppendEntries returns successfully, 
the leader knows that the follower’s log is identical to its own log up through the new entries.
#####
第一个特性源自这样一个事实，即一个leader只会在特定任期内的某一索引值下最多只会创建一个条目，并且日志条目在日志中的位置是永远不会改变的。
第二个特性则由AppendEntries执行一个简单的一致性检查来保证。
当发送AppendEntries RPC时，leader将前一个条目的索引和任期包含在新条目中。
如果follower没有找到一个具有相同索引值和任期的日志条目，则它将拒绝这一新条目。
一致性检查就像一个归纳的步骤:初始化时的空状态满足日志匹配的特性(Log Matching Property)，并且每当扩展日志时，一致性检查都会维持日志匹配的特性。
因此，每当AppendEntries返回成功时，通过新的条目leader就知道follower的日志与leader自己的是完全一致的，

#####
During normal operation, the logs of the leader and followers stay consistent,
so the AppendEntries consistency check never fails. 
However, leader crashes can leave the logs inconsistent (the old leader may not have fully replicated all of the entries in its log). 
These inconsistencies can compound over a series of leader and follower crashes.
Figure 7 illustrates the ways in which followers’ logs may differ from that of a new leader.
A follower may be missing entries that are present on the leader, it may have extra entries that are not present on the leader, or both.
Missing and extraneous entries in a log may span multiple terms.

![Figure7.png](Figure7.png)

#####
In Raft, the leader handles inconsistencies by forcing the followers’ logs to duplicate its own. 
This means that conflicting entries in follower logs will be overwritten with entries from the leader’s log. 
Section 5.4 will show that this is safe when coupled with one more restriction.

#####
To bring a follower’s log into consistency with its own, the leader must find the latest log entry where the two logs agree, 
delete any entries in the follower’s log after that point, and send the follower all of the leader’s entries after that point. 
All of these actions happen in response to the consistency check performed by AppendEntries RPCs.
The leader maintains a nextIndex for each follower, which is the index of the next log entry the leader will send to that follower. 
When a leader first comes to power, it initializes all nextIndex values to the index just after the last one in its log (11 in Figure 7). 
If a follower’s log is inconsistent with the leader’s, 
the AppendEntries consistency check will fail in the next AppendEntries RPC. 
After a rejection, the leader decrements nextIndex and retries the AppendEntries RPC.
Eventually nextIndex will reach a point where the leader and follower logs match.
When this happens, AppendEntries will succeed, 
which removes any conflicting entries in the follower’s log and appends entries from the leader’s log (if any). 
Once AppendEntries succeeds, the follower’s log is consistent with the leader’s, and it will remain that way for the rest of the term.

#####
If desired, the protocol can be optimized to reduce the number of rejected AppendEntries RPCs. 
For example, when rejecting an AppendEntries request, 
the follower can include the term of the conflicting entry and the first index it stores for that term. With this information, 
the leader can decrement nextIndex to bypass all of the conflicting entries in that term; 
one AppendEntries RPC will be required for each term with conflicting entries, rather than one RPC per entry.
In practice, we doubt this optimization is necessary, 
since failures happen infrequently and it is unlikely that there will be many inconsistent entries.

#####
With this mechanism, a leader does not need to take any special actions to restore log consistency when it comes to power. 
It just begins normal operation, and the logs automatically converge in response to failures of the AppendEntries consistency check. 
A leader never overwrites or deletes entries in its own log (the Leader Append-Only Property in Figure 3).

#####
This log replication mechanism exhibits the desirable consensus properties described in Section 2: 
Raft can accept, replicate, and apply new log entries as long as a majority of the servers are up; 
in the normal case a new entry can be replicated with a single round of RPCs to a majority of the cluster; 
and a single slow follower will not impact performance.

### 
