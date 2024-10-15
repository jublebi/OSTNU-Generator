# CSTNU Tool version 5.0

# 1. Introduction
This capsule provides source code and some execution samples (run script) of the Java library **CSTNU Tool**, presented in the article "CSTNU Tool: A Java Library for Checking Temporal Networks".
The article has been submitted to SoftwareX but has not been published. Please don't download these files, if you are not reviewers or editors.
Since it is not possible to run a GUI Java application inside a capsule, this capsule is just for showing how some classes/methods of the library can be used. For executing the GUI application `TNEditor`, please read the [CSTNU Tool usage page](https://profs.scienze.univr.it/~posenato/software/cstnu/usage.html).  

# 2. The uploaded files
### In /code
1. **/code/README.md** provides some descriptions of this capsule.
2. **/code/src** is the source code of CSTNU Tool. It is written in Java and organized as a Maven project.
3. **/code/run** is the script to compile and run three checks on temporal constraint networks, and a generation of 4 random networks. (See below)

### In /data
4. **/data/18Nodes2Obs5Ctg.cstnu** is a sample CSTNU network composed by 18 nodes, 2 observation nodes, and 5 contingent links. The network represents 4 possible alternative executions. In each of such executions, there are 3 contingent links. The presence of some global constraints between the first node and the last one requires to determine the right execution time for the controllable nodes. File **/data/18Nodes2Obs5Ctg.cstnu.png** is a screenshot of the **TNEditor* where the network is represented graphically.
5. **/data/113Nodes5obs.cstn** is a sample CSTN network composed by 113 nodes, and 5 observation nodes. The network represents the translation of a business workflow with 32 possible different executions. File **/data/113Nodes5obs.cstn.png** is a screenshot of the **TNEditor* where part of the network is represented graphically.
6. **/data/20210811SoftwareX.cstnu** is the CSTNU used as the main example in the SoftwareX article. File **/data/20210811SoftwareX.cstnu** is a screenshot of the **TNEditor* where the network is represented graphically.

# 3. Run script
The script is just for showing how some classes of the library can be called for checking the controllability of some networks.
It cannot show the GUI Java editor.

In particular, the script compiles the library, and runs three checks on temporal constraint networks, and runs a generation of 4 random networks:
1. compiles and runs the local class **SoftwareXExample** that is presented in the article. The class represents a console program that loads the network *20210811SoftwareX.cstnu* and checks its dynamic controllability.  
2. runs the `main` method of the class **CSTNU** for checking the dynamic controllability of CSTNU network *18Nodes2Obs5Ctg.ctnu*. The `main` method represents a simple code for loading, checking and saving a CSTNU instance. 
3. runs the `main` method of the class **CSTNPotential** for checking the dynamic controllability of CSTN network *113Nodes5Obs.cstn*.
4. runs the `main` method of the class **STNURandomGenerator** for generating 2 controllable and 2 not-controllable STNU instances.

In the `/code/run` file, there are comments that show how it is possible to customize such runs.

# 4. Results
`run` script reports the results of the checking to the console. 
Moreover, all resulting checked networks and the random-generated ones are saved in the `results` directory in GraphML format and (only for the random ones) in plain format.

# 5. File format
The CSTNU Tool supports only one file format: **GraphML**. 
There are currently two classes, **STNURandomGenerator** and **CSTNRandomGenerator** that generate random instances and save them in GraphML and in plain format. Such a *plain format* is a custom format required by the research group of prof. Luke Hunsberger. 

## 5.1 GraphML format
GraphML is an XML application for representing graphs of different types in a very flexible way.
In consists of two parts: a language core to describe the structural properties of a graph, and a flexible extension mechanism to add application-specific data.

In the language core, there are the elements `graph`, `node`, and `edge` by which it is possible to describe the topology of a graph.

The **GraphML-Attributes** extension allows the definition of node/ edge attributes in the same XML document where the graph is defined.

In the CSTNU Tool library, it is assumed that a GraphML document describing a temporal constraint network uses the following attributes (we report here the most significant):
```xml
<key id="Obs" for="node">
    <desc>Proposition Observed. Value specification: [a-zA-F]</desc>
</key>
<key id="Label" for="node">
    <desc>Label. Format: [¬[a-zA-F]|[a-zA-F]]+|⊡</desc>
    <default>⊡</default>
</key>
<key id="Potential" for="node">
    <desc>Labeled Potential Values. Format: {[('node name', 'integer', 'label') ]+}|{}</desc>
</key>
<key id="Type" for="edge">
    <desc>Type: Possible values: normal|contingent|constraint|derived|internal.</desc>
    <default>normal</default>
</key>
<key id="LowerCaseLabeledValues" for="edge">
    <desc>Labeled Lower-Case Values. Format: {[('node name', 'integer', 'label') ]+}|{}</desc>
</key>
<key id="UpperCaseLabeledValues" for="edge">
    <desc>Labeled Upper-Case Values. Format: {[('node name', 'integer', 'label') ]+}|{}</desc>
</key>
<key id="Value" for="edge">
    <desc>Value for STN edge. Format: 'integer'</desc>
</key>
<key id="LabeledValues" for="edge">
    <desc>Labeled Values. Format: {[('integer', 'label') ]+}|{}</desc>
</key>
```

Therefore, for example, to assign the labeled values <8,p> and <6,q> to the edge from node **C0** to node **X**, it is sufficient to add the attribute "LabeledValues" as shown in the following listing:
```xml
<edge id="c0x" source="C0" target="X">
    <data key="LabeledValues">{(8, p), (6, q) }</data>
</edge>
```