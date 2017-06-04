# gnowdb Data Model

This document provides complete details of the datamodel used in gnowdb.

## It is a Graph DB!

All data is managed in a graph database. Both schema and data are
represented as nodes, relation (edges) between nodes, and properties
of nodes and relations.

The data model is compatible to a triple store or an RDF store. The
schema can be defined as an ([Web Ontology Language
OWL](https://www.w3.org/OWL/))

The term *data element* in this document referes to nodes, edges and
their properties.

## Three types

The data model is simple. Data is designed by defining three kinds of major
classes of nodes. They are: **NodeTypes**, **AttributeTypes** and
**RelationTypes**.

Some default types are provided when we initialize an instance of gnowdb.

## Three tokens

The instances of the above types are **Nodes**, **Attributes** and **Relations**.

Some default tokens are provided when we initialize an instance of gnowdb.

## Unique ID for all elements

All data elements will have an ID. Use of UUID can be an
option. 

An an option, human readable display names of nodes can be held unique
based on each agency. Nodes cannot be published in more than one
agency without meeting the nodes have unique names. An option may be
provided to resolve the conflicts in such cases.

The ID of a node is called NID, and an ID of each temporal snapshot is
called SSID. Each snapshot can be referred to by ID+'.'+SSID.  For
example, 3.12 refers to 12th snapshot of node 3, where 3 is NID and 12
is SSID.  

## Data elements belong to one or more agencies.

An agent is a user, a group of users or organization or a
project. When no explicit assertion is used, the data elements will
**belong to** a defualt agency called **home**.  Default agencies are:
user and group created as instances of NodeTypes. 

**belongs_to** is the edge name used between a data element and agency,
  such that <data-element> <belongs to> <agency> can be asserted.

Use this to create subgraphs.

## Composing a schema as an Application

Design an application by collecting a set of NTs, ATs and RTs. Use an
edge **schema_element_of** between a type and an App node. This will
facilitate reusable applications by a design description.




