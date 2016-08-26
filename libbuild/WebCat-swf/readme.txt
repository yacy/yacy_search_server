What is WebCAT?

WebCAT is an extensible tool to extract meta-data and generate RDF descriptions from existing Web documents. 
Implemented in Java, it provides a set of APIs (Application Programming Interfaces) that allow one to analyse 
text documents from the Web without having to write complicated parsers.

Among other things, WebCAT provides:

  - Language and encoding detection.
  - Hyperlink extraction.
  - Text tokenization (words, n-grams, sentences).
  - Document fingerprinting.
  - Format conversion.
  - Metadata extraction and normalization.
  - Named Entity Extraction.
  - Document classification.

The considered meta-data elements are particularly suited to the domain of automated search, making this a 
good tool to use in other information retrieval and extraction projects. The software is currently in use 
at the tumba! Portuguese Web search engine, as part of the crawling module and as the basis of several text 
mining tools.

People
WebCAT was developed at the XLDB group of the Department of Informatics of the Faculty of Sciences of the University of Lisbon in Portugal.
WebCAT was written by Bruno Martins.


Research
WebCAT is a Java package for extracting and mining meta-data from Web documents. It is an important building 
block in several other text mining tools, handling most of the low level processing operations.

The software is divided in three "core" blocks: the parser, the miners and the output generator. The parser 
performs the "low-level" processing operations, receiving Web documents in HTML or converted from other 
different file formats. It analyzes the textual contents, divides the text into atomic units, and extracts 
hyper-links and meta-data. Afterwards, the miners make use of the parsed information, generating additional 
meta-data properties for the documents. Examples of miners include the language identification and 
classification modules. Finally, the output module produces RDF documents from the extracted/mined metadata.

Since the content and encoding of information on the Web is still mainly natural language (as oposed to more 
machine understandable formats such as RDF), automated large scale information extraction can bootstrap and 
accelerate the creation of a semantic web.

Availability
WebCAT is released under the BSD License, which basically states that you can do anything you like with it 
as long as you mention the authors and make it clear that the library is covered by the BSD License. It also 
exempts us from any liability, should this library eat your hard disc or kill your cat.

Source code, samples and detailed documentation are provided in the download. The Java API documentation is 
also available online.

The package is relatively simple install and run. We encourage you to try it out and let us know of any 
problems you find. 
We would also be very happy to hear from people who are using this software package.
