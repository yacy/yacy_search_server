A vocabulary is used to produce search navigation entities.
A search navigation is what you see at the right column
at the side of a search results where it is possible to reduce the
set of result entries with given restrictions.

A vocabulary is a restriction where the search results are restricted to
entries which have a specific tag in the subject metadata that corresponds
to the vocabulary restriction. The restriction is expressed with a set of
synonyms for the tag in a property-like file. Such files are activated if
they are present in the folder DATA/DICTIONARIES/autotagging/ at start-up time
and the vocabulary files must be named with a '.vocabulary' extension.

Vocabulary files are similar to property-files with these rules rules:
- the key represents the vocabulary term (this is what you see in the navigation)
- a value is a list of synonyms for the vocabulary term
- a term is always self-referencing (the term is also a synonym for the term)
- a value may be omitted (a self-referencing-only vocabulary)

The format of a vocabulary file is:
each line has the format
<print-name>[=<synonym>{','<synonym>}*]
or the line starts with a '#' for comment lines

The subdirectories of this directory contains example-vocabularies for
specific languages. Vocabularies work best if the vocabulary is expressed in
the same language as the documents are that are indexed.

A vocabulary can be activated by doing:
- copy the vocabulary from the <lang>/ subdirectory to DATA/DICTIONARIES/autotagging/
- restart
- do an indexing of the web-pages. Vocabularies cannot be applied to already indexed
  web pages because tags are only generated during the parsing process
