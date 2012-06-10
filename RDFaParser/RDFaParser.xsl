<?xml version="1.0" encoding="UTF-8"?>
<stylesheet 
xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	version="1.0" xmlns:h="http://www.w3.org/1999/xhtml" 
	xmlns="http://www.w3.org/1999/XSL/Transform"
	xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" 
	xmlns:xalan="http://xml.apache.org/xalan"
	xmlns:java="http://xml.apache.org/xalan/java" 
	exclude-result-prefixes="java"
	>


	<!-- Version 0.21 by Fabien.Gandon@sophia.inria.fr -->
	<!-- This software is distributed under either the CeCILL-C license or the 
		GNU Lesser General Public License version 3 license. -->
	<!-- This program is free software: you can redistribute it and/or modify 
		it under the terms of the GNU Lesser General Public License -->
	<!-- as published by the Free Software Foundation version 3 of the License 
		or under the terms of the CeCILL-C license. -->
	<!-- This program is distributed in the hope that it will be useful, but 
		WITHOUT ANY WARRANTY; without even the implied -->
	<!-- warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. -->
	<!-- See the GNU Lesser General Public License version 3 at http://www.gnu.org/licenses/ -->
	<!-- and the CeCILL-C license at http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html 
		for more details -->


	<output indent="yes" method="xml" media-type="application/rdf+xml"
		encoding="UTF-8" omit-xml-declaration="yes" />

	<!-- base of the current HTML doc -->
	<variable name='html_base' select="//*/h:head/h:base[position()=1]/@href" />

	<!-- default HTML vocabulary namespace -->
	<variable name='default_voc' select="'http://www.w3.org/1999/xhtml/vocab#'" />

	<!-- parser instance -->
	<param name='parser' select="''" />

	<!-- url of the current XHTML page if provided by the XSLT engine -->
	<param name='url' select="''" />

	<!-- this contains the URL of the source document whether it was provided 
		by the base or as a parameter e.g. http://example.org/bla/file.html -->
	<variable name='this'>
		<choose>
			<when test="string-length($html_base)>0">
				<value-of select="$html_base" />
			</when>
			<otherwise>
				<value-of select="$url" />
			</otherwise>
		</choose>
	</variable>

	<!-- this_location contains the location the source document e.g. http://example.org/bla/ -->
	<variable name='this_location'>
		<call-template name="get-location">
			<with-param name="url" select="$this" />
		</call-template>
	</variable>

	<!-- this_root contains the root location of the source document e.g. http://example.org/ -->
	<variable name='this_root'>
		<call-template name="get-root">
			<with-param name="url" select="$this" />
		</call-template>
	</variable>


	<!-- templates for parsing - - - - - - - - - - - - - - - - - - - - - - - 
		- - - - - - - - - - - - - -->

	<!--Start the RDF generation -->
	<template match="/">
		<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
			<apply-templates mode="rdf2rdfxml" />  <!-- the mode is used to ease integration with other XSLT templates -->
		</rdf:RDF>
	</template>


	<!-- match RDFa element -->
	<template
		match="*[attribute::property or attribute::rel or attribute::rev or attribute::typeof]"
		mode="rdf2rdfxml">

		<!-- identify suject -->
		<variable name="subject">
			<call-template name="subject" />
		</variable>


		<!-- do we have object properties? -->
		<if test="string-length(@rel)>0 or string-length(@rev)>0">
			<variable name="object"> <!-- identify the object(s) -->
				<choose>
					<when test="@resource">
						<call-template name="expand-curie-or-uri">
							<with-param name="curie_or_uri" select="@resource" />
						</call-template>
					</when>
					<when test="@href">
						<call-template name="expand-curie-or-uri">
							<with-param name="curie_or_uri" select="@href" />
						</call-template>
					</when>
					<when
						test="descendant::*[attribute::about or attribute::src or attribute::typeof or
	     	 attribute::href or attribute::resource or
	     	 attribute::rel or attribute::rev or attribute::property]">
						<call-template name="recurse-objects" />
					</when>
					<otherwise>
						<call-template name="self-curie-or-uri">
							<with-param name="node" select="." />
						</call-template>
					</otherwise>
				</choose>
			</variable>

			<call-template name="relrev">
				<with-param name="subject" select="$subject" />
				<with-param name="object" select="$object" />
			</call-template>

		</if>


		<!-- do we have data properties ? -->
		<if test="string-length(@property)>0">

			<!-- identify language -->
			<variable name="language"
				select="string(ancestor-or-self::*/attribute::xml:lang[position()=1])" />

			<variable name="expended-pro">
				<call-template name="expand-ns">
					<with-param name="qname" select="@property" />
				</call-template>
			</variable>

			<choose>
				<when test="@content"> <!-- there is a specific content -->
					<call-template name="property">
						<with-param name="subject" select="$subject" />
						<with-param name="object" select="@content" />
						<with-param name="datatype">
							<choose>
								<when test="@datatype='' or not(@datatype)"></when> <!-- enforcing plain literal -->
								<otherwise>
									<call-template name="expand-ns">
										<with-param name="qname" select="@datatype" />
									</call-template>
								</otherwise>
							</choose>
						</with-param>
						<with-param name="predicate" select="@property" />
						<with-param name="attrib" select="'true'" />
						<with-param name="language" select="$language" />
					</call-template>
				</when>
				<when test="not(*)"> <!-- there no specific content but there are no children elements in the 
						content -->
					<call-template name="property">
						<with-param name="subject" select="$subject" />
						<with-param name="object" select="." />
						<with-param name="datatype">
							<choose>
								<when test="@datatype='' or not(@datatype)"></when> <!-- enforcing plain literal -->
								<otherwise>
									<call-template name="expand-ns">
										<with-param name="qname" select="@datatype" />
									</call-template>
								</otherwise>
							</choose>
						</with-param>
						<with-param name="predicate" select="@property" />
						<with-param name="attrib" select="'true'" />
						<with-param name="language" select="$language" />
					</call-template>
				</when>
				<otherwise> <!-- there is no specific content; we use the value of element -->
					<call-template name="property">
						<with-param name="subject" select="$subject" />
						<with-param name="object" select="." />
						<with-param name="datatype">
							<choose>
								<when test="@datatype='' or not(@datatype)">
									http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral
								</when> <!-- enforcing XML literal -->
								<otherwise>
									<call-template name="expand-ns">
										<with-param name="qname" select="@datatype" />
									</call-template>
								</otherwise>
							</choose>
						</with-param>
						<with-param name="predicate" select="@property" />
						<with-param name="attrib" select="'false'" />
						<with-param name="language" select="$language" />
					</call-template>
				</otherwise>
			</choose>
		</if>

		<!-- do we have classes ? -->
		<if test="@typeof">
			<call-template name="class">
				<with-param name="resource">
					<call-template name="self-curie-or-uri">
						<with-param name="node" select="." />
					</call-template>
				</with-param>
				<with-param name="class" select="@typeof" />
			</call-template>
		</if>

		<apply-templates mode="rdf2rdfxml" />

	</template>


	<!-- named templates to process URIs and token lists - - - - - - - - - - 
		- - - - - - - - - - - - - - - - - - - - - - - - - - -->

	<!-- tokenize a string using space as a delimiter -->
	<template name="tokenize">
		<param name="string" />
		<if test="string-length($string)>0">
			<choose>
				<when test="contains($string,' ')">
					<value-of select="normalize-space(substring-before($string,' '))" />
					<call-template name="tokenize">
						<with-param name="string"
							select="normalize-space(substring-after($string,' '))" />
					</call-template>
				</when>
				<otherwise>
					<value-of select="$string" />
				</otherwise>
			</choose>
		</if>
	</template>

	<!-- get file location from URL -->
	<template name="get-location">
		<param name="url" />
		<if test="string-length($url)>0 and contains($url,'/')">
			<value-of select="concat(substring-before($url,'/'),'/')" />
			<call-template name="get-location">
				<with-param name="url" select="substring-after($url,'/')" />
			</call-template>
		</if>
	</template>

	<!-- get root location from URL -->
	<template name="get-root">
		<param name="url" />
		<choose>
			<when test="contains($url,'//')">
				<value-of
					select="concat(substring-before($url,'//'),'//',substring-before(substring-after($url,'//'),'/'),'/')" />
			</when>
			<otherwise>
				UNKNOWN ROOT
			</otherwise>
		</choose>
	</template>

	<!-- return namespace of a qname -->
	<template name="return-ns">
		<param name="qname" />
		<variable name="ns_prefix" select="substring-before($qname,':')" />
		<if test="string-length($ns_prefix)>0"> <!-- prefix must be explicit -->
			<variable name="name" select="substring-after($qname,':')" />
			<value-of
				select="ancestor-or-self::*/namespace::*[name()=$ns_prefix][position()=1]" />
		</if>
		<if
			test="string-length($ns_prefix)=0 and ancestor-or-self::*/namespace::*[name()=''][position()=1]"> <!-- no prefix -->
			<variable name="name" select="substring-after($qname,':')" />
			<value-of select="ancestor-or-self::*/namespace::*[name()=''][position()=1]" />
		</if>
	</template>


	<!-- expand namespace of a qname -->
	<template name="expand-ns">
		<param name="qname" />
		<variable name="ns_prefix" select="substring-before($qname,':')" />
		<if test="string-length($ns_prefix)>0"> <!-- prefix must be explicit -->
			<variable name="name" select="substring-after($qname,':')" />
			<variable name="ns_uri"
				select="ancestor-or-self::*/namespace::*[name()=$ns_prefix][position()=1]" />
			<value-of select="concat($ns_uri,$name)" />
		</if>
		<if
			test="string-length($ns_prefix)=0 and ancestor-or-self::*/namespace::*[name()=''][position()=1]"> <!-- no prefix -->
			<variable name="name" select="substring-after($qname,':')" />
			<variable name="ns_uri"
				select="ancestor-or-self::*/namespace::*[name()=''][position()=1]" />
			<value-of select="concat($ns_uri,$name)" />
		</if>
	</template>

	<!-- determines the CURIE / URI of a node -->
	<template name="self-curie-or-uri">
		<param name="node" />
		<choose>
			<when test="$node/attribute::about"> <!-- we have an about attribute to extend -->
				<call-template name="expand-curie-or-uri">
					<with-param name="curie_or_uri" select="$node/attribute::about" />
				</call-template>
			</when>
			<when test="$node/attribute::src"> <!-- we have an src attribute to extend -->
				<call-template name="expand-curie-or-uri">
					<with-param name="curie_or_uri" select="$node/attribute::src" />
				</call-template>
			</when>
			<when
				test="$node/attribute::resource and not($node/attribute::rel or $node/attribute::rev)"> <!-- enforcing the resource as subject if no rel or rev -->
				<call-template name="expand-curie-or-uri">
					<with-param name="curie_or_uri" select="$node/attribute::resource" />
				</call-template>
			</when>
			<when
				test="$node/attribute::href and not($node/attribute::rel or $node/attribute::rev)"> <!-- enforcing the href as subject if no rel or rev -->
				<call-template name="expand-curie-or-uri">
					<with-param name="curie_or_uri" select="$node/attribute::href" />
				</call-template>
			</when>
			<when
				test="$node/self::h:head or $node/self::h:body or $node/self::h:html">
				<value-of select="$this" />
			</when> <!-- enforcing the doc as subject -->
			<when test="$node/attribute::id"> <!-- we have an id attribute to extend -->
				<value-of select="concat($this,'#',$node/attribute::id)" />
			</when>
			<otherwise>
				blank:node:
				<value-of select="generate-id($node)" />
			</otherwise>
		</choose>
	</template>


	<!-- expand CURIE / URI -->
	<template name="expand-curie-or-uri">
		<param name="curie_or_uri" />
		<choose>
			<when test="starts-with($curie_or_uri,'[_:')"> <!-- we have a CURIE blank node -->
				<value-of
					select="concat('blank:node:',substring-after(substring-before($curie_or_uri,']'),'[_:'))" />
			</when>
			<when test="starts-with($curie_or_uri,'[')"> <!-- we have a CURIE between square brackets -->
				<call-template name="expand-ns">
					<with-param name="qname"
						select="substring-after(substring-before($curie_or_uri,']'),'[')" />
				</call-template>
			</when>
			<when test="starts-with($curie_or_uri,'#')"> <!-- we have an anchor -->
				<value-of select="concat($this,$curie_or_uri)" />
			</when>
			<when test="string-length($curie_or_uri)=0"> <!-- empty anchor means the document itself -->
				<value-of select="$this" />
			</when>
			<when
				test="not(starts-with($curie_or_uri,'[')) and contains($curie_or_uri,':')"> <!-- it is a URI -->
				<value-of select="$curie_or_uri" />
			</when>
			<when
				test="not(contains($curie_or_uri,'://')) and not(starts-with($curie_or_uri,'/'))"> <!-- relative URL -->
				<value-of select="concat($this_location,$curie_or_uri)" />
			</when>
			<when
				test="not(contains($curie_or_uri,'://')) and (starts-with($curie_or_uri,'/'))"> <!-- URL from root domain -->
				<value-of select="concat($this_root,substring-after($curie_or_uri,'/'))" />
			</when>
			<otherwise>
				UNKNOWN CURIE URI
			</otherwise>
		</choose>
	</template>

	<!-- returns the first token in a list separated by spaces -->
	<template name="get-first-token">
		<param name="tokens" />
		<if test="string-length($tokens)>0">
			<choose>
				<when test="contains($tokens,' ')">
					<value-of select="normalize-space(substring-before($tokens,' '))" />
				</when>
				<otherwise>
					<value-of select="$tokens" />
				</otherwise>
			</choose>
		</if>
	</template>

	<!-- returns the namespace for an object property -->
	<template name="get-relrev-ns">
		<param name="qname" />
		<variable name="ns_prefix"
			select="substring-before(translate($qname,'[]',''),':')" />
		<choose>
			<when test="string-length($ns_prefix)>0">
				<call-template name="return-ns">
					<with-param name="qname" select="$qname" />
				</call-template>
			</when>
			<!-- returns default_voc if the predicate is a reserved value -->
			<otherwise>
				<variable name="is-reserved">
					<call-template name="check-reserved">
						<with-param name="nonprefixed">
							<call-template name="no-leading-colon">
								<with-param name="name" select="$qname" />
							</call-template>
						</with-param>
					</call-template>
				</variable>
				<if test="$is-reserved='true'">
					<value-of select="$default_voc" />
				</if>
			</otherwise>
		</choose>
	</template>

	<!-- returns the namespace for a data property -->
	<template name="get-property-ns">
		<param name="qname" />
		<variable name="ns_prefix"
			select="substring-before(translate($qname,'[]',''),':')" />
		<choose>
			<when test="string-length($ns_prefix)>0">
				<call-template name="return-ns">
					<with-param name="qname" select="$qname" />
				</call-template>
			</when>
			<!-- returns default_voc otherwise -->
			<otherwise>
				<value-of select="$default_voc" />
			</otherwise>
		</choose>
	</template>

	<!-- returns the qname for a predicate -->
	<template name="get-predicate-name">
		<param name="qname" />
		<variable name="clean_name" select="translate($qname,'[]','')" />
		<call-template name="no-leading-colon">
			<with-param name="name" select="$clean_name" />
		</call-template>
	</template>

	<!-- no leading colon -->
	<template name="no-leading-colon">
		<param name="name" />
		<choose>
			<when test="starts-with($name,':')"> <!-- remove leading colons -->
				<value-of select="substring-after($name,':')" />
			</when>
			<otherwise>
				<value-of select="$name" />
			</otherwise>
		</choose>
	</template>

	<!-- check if a predicate is reserved -->
	<template name="check-reserved">
		<param name="nonprefixed" />
		<choose>
			<when
				test="$nonprefixed='alternate' or $nonprefixed='appendix' or $nonprefixed='bookmark' or $nonprefixed='cite'">
				true
			</when>
			<when
				test="$nonprefixed='chapter' or $nonprefixed='contents' or $nonprefixed='copyright' or $nonprefixed='first'">
				true
			</when>
			<when
				test="$nonprefixed='glossary' or $nonprefixed='help' or $nonprefixed='icon' or $nonprefixed='index'">
				true
			</when>
			<when
				test="$nonprefixed='last' or $nonprefixed='license' or $nonprefixed='meta' or $nonprefixed='next'">
				true
			</when>
			<when
				test="$nonprefixed='p3pv1' or $nonprefixed='prev' or $nonprefixed='role' or $nonprefixed='section'">
				true
			</when>
			<when
				test="$nonprefixed='stylesheet' or $nonprefixed='subsection' or $nonprefixed='start' or $nonprefixed='top'">
				true
			</when>
			<when test="$nonprefixed='up'">
				true
			</when>
			<when
				test="$nonprefixed='made' or $nonprefixed='previous' or $nonprefixed='search'">
				true
			</when>  <!-- added because they are frequent -->
			<otherwise>
				false
			</otherwise>
		</choose>
	</template>

	<!-- named templates to generate RDF - - - - - - - - - - - - - - - - - - 
		- - - - - - - - - - - - - - -->

	<template name="recursive-copy"> <!-- full copy -->
		<copy>
			<for-each select="node()|attribute::* ">
				<call-template name="recursive-copy" />
			</for-each>
		</copy>
	</template>


	<template name="subject"> <!-- determines current subject -->
		<choose>

			<!-- current node is a meta or a link in the head and with no about attribute -->
			<when
				test="(self::h:link or self::h:meta) and ( ancestor::h:head ) and not(attribute::about)">
				<value-of select="$this" />
			</when>

			<!-- an attribute about was specified on the node -->
			<when test="self::*/attribute::about">
				<call-template name="expand-curie-or-uri">
					<with-param name="curie_or_uri" select="@about" />
				</call-template>
			</when>

			<!-- an attribute src was specified on the node -->
			<when test="self::*/attribute::src">
				<call-template name="expand-curie-or-uri">
					<with-param name="curie_or_uri" select="@src" />
				</call-template>
			</when>


			<!-- an attribute typeof was specified on the node -->
			<when test="self::*/attribute::typeof">
				<call-template name="self-curie-or-uri">
					<with-param name="node" select="." />
				</call-template>
			</when>

			<!-- current node is a meta or a link in the body and with no about attribute -->
			<when
				test="(self::h:link or self::h:meta) and not( ancestor::h:head ) and not(attribute::about)">
				<call-template name="self-curie-or-uri">
					<with-param name="node" select="parent::*" />
				</call-template>
			</when>

			<!-- an about was specified on its parent or the parent had a rel or a 
				rev attribute but no href or an typeof. -->
			<when
				test="ancestor::*[attribute::about or attribute::src or attribute::typeof or attribute::resource or attribute::href or attribute::rel or attribute::rev][position()=1]">
				<variable name="selected_ancestor"
					select="ancestor::*[attribute::about or attribute::src or attribute::typeof or attribute::resource or attribute::href or attribute::rel or attribute::rev][position()=1]" />
				<choose>
					<when
						test="$selected_ancestor[(attribute::rel or attribute::rev) and not (attribute::resource or attribute::href)]">
						<value-of
							select="concat('blank:node:INSIDE_',generate-id($selected_ancestor))" />
					</when>
					<when test="$selected_ancestor/attribute::about">
						<call-template name="expand-curie-or-uri">
							<with-param name="curie_or_uri" select="$selected_ancestor/attribute::about" />
						</call-template>
					</when>
					<when test="$selected_ancestor/attribute::src">
						<call-template name="expand-curie-or-uri">
							<with-param name="curie_or_uri" select="$selected_ancestor/attribute::src" />
						</call-template>
					</when>
					<when test="$selected_ancestor/attribute::resource">
						<call-template name="expand-curie-or-uri">
							<with-param name="curie_or_uri"
								select="$selected_ancestor/attribute::resource" />
						</call-template>
					</when>
					<when test="$selected_ancestor/attribute::href">
						<call-template name="expand-curie-or-uri">
							<with-param name="curie_or_uri" select="$selected_ancestor/attribute::href" />
						</call-template>
					</when>
					<otherwise>
						<call-template name="self-curie-or-uri">
							<with-param name="node" select="$selected_ancestor" />
						</call-template>
					</otherwise>
				</choose>
			</when>

			<otherwise> <!-- it must be about the current document -->
				<value-of select="$this" />
			</otherwise>

		</choose>
	</template>

	<!-- recursive call for object(s) of object properties -->
	<template name="recurse-objects">
		<xsl:for-each select="child::*">
			<choose>
				<when test="attribute::about or attribute::src"> <!-- there is a known resource -->
					<call-template name="expand-curie-or-uri">
						<with-param name="curie_or_uri" select="attribute::about | attribute::src" />
					</call-template>
					<text> </text>
				</when>
				<when
					test="(attribute::resource or attribute::href) and ( not (attribute::rel or attribute::rev or attribute::property))"> <!-- there is an incomplet triple -->
					<call-template name="expand-curie-or-uri">
						<with-param name="curie_or_uri"
							select="attribute::resource | attribute::href" />
					</call-template>
					<text> </text>
				</when>
				<when test="attribute::typeof and not (attribute::about)"> <!-- there is an implicit resource -->
					<call-template name="self-curie-or-uri">
						<with-param name="node" select="." />
					</call-template>
					<text> </text>
				</when>
				<when test="attribute::rel or attribute::rev or attribute::property"> <!-- there is an implicit resource -->
					<if
						test="not (preceding-sibling::*[attribute::rel or attribute::rev or attribute::property])"> <!-- generate the triple only once -->
						<call-template name="subject" />
						<text> </text>
					</if>
				</when>
				<otherwise> <!-- nothing at that level thus consider children -->
					<call-template name="recurse-objects" />
				</otherwise>
			</choose>
		</xsl:for-each>
	</template>

	<!-- generate recursive call for multiple objects in rel or rev -->
	<template name="relrev">
		<param name="subject" />
		<param name="object" />

		<!-- test for multiple predicates -->
		<variable name="single-object">
			<call-template name="get-first-token">
				<with-param name="tokens" select="$object" />
			</call-template>
		</variable>

		<if test="string-length(@rel)>0">
			<call-template name="relation">
				<with-param name="subject" select="$subject" />
				<with-param name="object" select="$single-object" />
				<with-param name="predicate" select="@rel" />
			</call-template>
		</if>

		<if test="string-length(@rev)>0">
			<call-template name="relation">
				<with-param name="subject" select="$single-object" />
				<with-param name="object" select="$subject" />
				<with-param name="predicate" select="@rev" />
			</call-template>
		</if>

		<!-- recursive call for multiple predicates -->
		<variable name="other-objects"
			select="normalize-space(substring-after($object,' '))" />
		<if test="string-length($other-objects)>0">
			<call-template name="relrev">
				<with-param name="subject" select="$subject" />
				<with-param name="object" select="$other-objects" />
			</call-template>
		</if>

	</template>


	<!-- generate an RDF statement for a relation -->
	<template name="relation">
		<param name="subject" />
		<param name="predicate" />
		<param name="object" />

		<!-- test for multiple predicates -->
		<variable name="single-predicate">
			<call-template name="get-first-token">
				<with-param name="tokens" select="$predicate" />
			</call-template>
		</variable>

		<!-- get namespace of the predicate -->
		<variable name="predicate-ns">
			<call-template name="get-relrev-ns">
				<with-param name="qname" select="$single-predicate" />
			</call-template>
		</variable>

		<!-- get name of the predicate -->
		<variable name="predicate-name">
			<call-template name="get-predicate-name">
				<with-param name="qname" select="$single-predicate" />
			</call-template>
		</variable>

		<choose>
			<when test="string-length($predicate-ns)>0"> <!-- there is a known namespace for the predicate -->
				<choose>
					<when test="starts-with($subject,'blank:node:')">
						<value-of
							select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheSubjectNodeID($parser,substring-after($subject,'blank:node:'))" />
					</when>
					<otherwise>
						<value-of
							select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheSubjectURI($parser,$subject)" />
					</otherwise>
				</choose>
				<!-- get full predicate -->
				<variable name="expanded-predicate">
					<call-template name="expand-ns">
						<with-param name="qname" select="$single-predicate" />
					</call-template>
				</variable>
				<value-of
					select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setThePropertyURI($parser,$expanded-predicate)" />

				<choose>
					<when test="starts-with($object,'blank:node:')">
						<value-of
							select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheObjectNodeID($parser,substring-after($object,'blank:node:'))" />
					</when>
					<otherwise>
						<value-of
							select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheObjectURI($parser,$object)" />
					</otherwise>
				</choose>
			</when>
			<otherwise> <!-- no namespace generate a comment for debug -->
				<xsl:comment>
					No namespace for the rel or rev value ; could not produce the
					triple for:
					<value-of select="$subject" />
					-
					<value-of select="$single-predicate" />
					-
					<value-of select="$object" />
				</xsl:comment>
			</otherwise>
		</choose>

		<value-of
			select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.flushObjectProperty($parser)" />

		<!-- recursive call for multiple predicates -->
		<variable name="other-predicates"
			select="normalize-space(substring-after($predicate,' '))" />
		<if test="string-length($other-predicates)>0">
			<call-template name="relation">
				<with-param name="subject" select="$subject" />
				<with-param name="predicate" select="$other-predicates" />
				<with-param name="object" select="$object" />
			</call-template>
		</if>

	</template>


	<!-- generate an RDF statement for a property -->
	<template name="property">
		<param name="subject" />
		<param name="predicate" />
		<param name="object" />
		<param name="datatype" />
		<param name="attrib" /> <!-- is the content from an attribute ? true /false -->
		<param name="language" />

		<!-- test for multiple predicates -->
		<variable name="single-predicate">
			<call-template name="get-first-token">
				<with-param name="tokens" select="$predicate" />
			</call-template>
		</variable>

		<!-- get namespace of the predicate -->
		<variable name="predicate-ns">
			<call-template name="get-property-ns">
				<with-param name="qname" select="$single-predicate" />
			</call-template>
		</variable>


		<!-- get name of the predicate -->
		<variable name="predicate-name">
			<call-template name="get-predicate-name">
				<with-param name="qname" select="$single-predicate" />
			</call-template>
		</variable>

		<choose>
			<when test="string-length($predicate-ns)>0"> <!-- there is a known namespace for the predicate -->

				<value-of
					select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setThePropertyURI($parser,$predicate-name)" />

				<choose>
					<when test="starts-with($subject,'blank:nod: ')">
						<value-of
							select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheSubjectNodeID($parser,substring-after($subject,'blank:node:'))" />
					</when>
					<otherwise>
						<value-of
							select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheSubjectURI($parser,$subject)" />
					</otherwise>
				</choose>

				<if test="string-length($language)>0">
					<value-of
						select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheLanguage($parser,$language)" />
				</if>

				<choose>
					<when
						test="$datatype='http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral'">
						<choose>
							<when test="$attrib='true'"> <!-- content is in an attribute -->
								<value-of
									select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheDatatype($parser,$datatype)" />
								<value-of
									select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheValue($parser,normalize-space(string($object)))" />
							</when>
							<otherwise> <!-- content is in the element and may include some tags -->
								<value-of
									select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheDatatype($parser,$datatype)" />
								<value-of
									select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheValue($parser,$object)" />
							</otherwise>
						</choose>
					</when>
					<when test="string-length($datatype)>0">
						<!-- there is a datatype other than XMLLiteral -->
						<value-of
							select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheDatatype($parser,$datatype)" />
						<choose>
							<when test="$attrib='true'"> <!-- content is in an attribute -->
								<value-of
									select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheValue($parser,normalize-space(string($object)))" />
							</when>
							<otherwise> <!-- content is in the text nodes of the element -->
								<value-of
									select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheValue($parser,$object)" />
							</otherwise>
						</choose>
					</when>
					<otherwise> <!-- there is no datatype -->
						<choose>
							<when test="$attrib='true'"> <!-- content is in an attribute -->
								<value-of
									select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheValue($parser,normalize-space(string($object)))" />
							</when>
							<otherwise> <!-- content is in the text nodes of the element -->
								<value-of
									select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheValue($parser,$object)" />
							</otherwise>
						</choose>
					</otherwise>
				</choose>
			</when>
			<otherwise> <!-- generate a comment for debug -->
				<xsl:comment>
					Could not produce the triple for:
					<value-of select="$subject" />
					-
					<value-of select="$single-predicate" />
					-
					<value-of select="$object" />
				</xsl:comment>
			</otherwise>
		</choose>

		<value-of
			select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.flushDataProperty($parser)" />

		<!-- recursive call for multiple predicates -->
		<variable name="other-predicates"
			select="normalize-space(substring-after($predicate,' '))" />
		<if test="string-length($other-predicates)>0">
			<call-template name="property">
				<with-param name="subject" select="$subject" />
				<with-param name="predicate" select="$other-predicates" />
				<with-param name="object" select="$object" />
				<with-param name="datatype" select="$datatype" />
				<with-param name="attrib" select="$attrib" />
				<with-param name="language" select="$language" />
			</call-template>
		</if>

	</template>

	<!-- generate an RDF statement for a class -->
	<template name="class">
		<param name="resource" />
		<param name="class" />

		<!-- case multiple classes -->
		<variable name="single-class">
			<call-template name="get-first-token">
				<with-param name="tokens" select="$class" />
			</call-template>
		</variable>

		<!-- get namespace of the class -->
		<variable name="class-ns">
			<call-template name="return-ns">
				<with-param name="qname" select="$single-class" />
			</call-template>
		</variable>

		<if test="string-length($class-ns)>0"> <!-- we have a qname for the class -->
			<variable name="expended-class">
				<call-template name="expand-ns">
					<with-param name="qname" select="$single-class" />
				</call-template>
			</variable>
			<choose>
				<when test="starts-with($resource,'blank:node:')">
					<value-of
						select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheSubjectNodeID($parser,substring-after($resource,'blank:node:'))" />
				</when>
				<otherwise>
					<value-of
						select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheSubjectURI($parser,$resource)" />
				</otherwise>
			</choose>
			<value-of
				select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setThePropertyURI($parser,'http://www.w3.org/1999/02/22-rdf-syntax-ns#type')" />
			<value-of
				select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.setTheObjectURI($parser,$expended-class)" />
			<value-of
				select="java:net.yacy.document.parser.rdfa.impl.RDFaTripleImpl.flushObjectProperty($parser)" />
		</if>


		<!-- recursive call for multiple classes -->
		<variable name="other-classes" select="normalize-space(substring-after($class,' '))" />
		<if test="string-length($other-classes)>0">
			<call-template name="class">
				<with-param name="resource" select="$resource" />
				<with-param name="class" select="$other-classes" />
			</call-template>
		</if>

	</template>


	<!-- ignore the rest of the DOM -->
	<template match="text()|@*|*" mode="rdf2rdfxml">
		<apply-templates mode="rdf2rdfxml" />
	</template>


</stylesheet>
