/**
 *  CreativeCommons
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 17.12.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.lod.vocabulary;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.lod.Literal;
import net.yacy.cora.lod.Vocabulary;

/**
 * a vocabulary for creative commons license declarations. see:
 * http://creativecommons.org/ns#
 */
public enum CreativeCommons implements Vocabulary {
    
    // License Properties
    permits(new Literal[]{
        PermitLiteral.Reproduction,
        PermitLiteral.Distribution,
        PermitLiteral.DerivativeWorks,
        PermitLiteral.Sharing}),
    requires(new Literal[]{
        RequirementLiteral.Notice,
        RequirementLiteral.Attribution,
        RequirementLiteral.ShareAlike,
        RequirementLiteral.SourceCode,
        RequirementLiteral.Copyleft,
        RequirementLiteral.LesserCopyleft}),
    prohibits(new Literal[]{
        ProhibitionLiteral.CommercialUse,
        ProhibitionLiteral.HighIncomeNationUse}),
    jurisdiction,
    legalcode,
    deprecatedOn,
    
    // Work Properties
    license,
    morePermissions,
    attributionName,
    attributionURL,
    useGuidelines;

    enum PermitLiteral implements Literal {
        
        Reproduction("Reproduction", "http://creativecommons.org/ns#Permission", ".*"),
        Distribution("Distribution", "http://creativecommons.org/ns#Permission", ".*"),
        DerivativeWorks("Derivative Works", "http://creativecommons.org/ns#Permission", ".*"),
        Sharing("Sharing", "http://creativecommons.org/ns#Permission", ".*");
        
        String terminal;
        MultiProtocolURI subject;
        Pattern discoveryPattern;
        
        private PermitLiteral(
                String terminal,
                String subject,
                String discoveryPattern) {
            this.terminal = terminal;
            try {
                this.subject = subject == null ? null : new MultiProtocolURI(subject);
            } catch (MalformedURLException e) {
                this.subject = null;
            }
            this.discoveryPattern = Pattern.compile(discoveryPattern == null ? ".*" : discoveryPattern);
        }
        
        @Override
        public String getTerminal() { return this.terminal; }

        @Override
        public MultiProtocolURI getSubject() { return this.subject; }

        @Override
        public Pattern getDiscoveryPattern() { return this.discoveryPattern; }
    }
    
    enum RequirementLiteral implements Literal {
        
        Notice("Notice", "http://creativecommons.org/ns#Requirement", ".*"),
        Attribution("Attribution", "http://creativecommons.org/ns#Requirement", ".*"),
        ShareAlike("Share Alike", "http://creativecommons.org/ns#Requirement", ".*"),
        SourceCode("Source Code", "http://creativecommons.org/ns#Requirement", ".*"),
        Copyleft("Copyleft", "http://creativecommons.org/ns#Requirement", ".*"),
        LesserCopyleft("Lesser Copyleft", "http://creativecommons.org/ns#Requirement", ".*");
        
        String terminal;
        MultiProtocolURI subject;
        Pattern discoveryPattern;
        
        private RequirementLiteral(
                String terminal,
                String subject,
                String discoveryPattern) {
            this.terminal = terminal;
            try {
                this.subject = subject == null ? null : new MultiProtocolURI(subject);
            } catch (MalformedURLException e) {
                this.subject = null;
            }
            this.discoveryPattern = Pattern.compile(discoveryPattern == null ? ".*" : discoveryPattern);
        }
        
        @Override
        public String getTerminal() { return this.terminal; }

        @Override
        public MultiProtocolURI getSubject() { return this.subject; }

        @Override
        public Pattern getDiscoveryPattern() { return this.discoveryPattern; }
    }

    enum ProhibitionLiteral implements Literal {
        
        CommercialUse("Commercial Use", "http://creativecommons.org/ns#Prohibition", ".*"),
        HighIncomeNationUse("High Income Nation Use", "http://creativecommons.org/ns#Prohibition", ".*");
        
        String terminal;
        MultiProtocolURI subject;
        Pattern discoveryPattern;
        
        private ProhibitionLiteral(
                String terminal,
                String subject,
                String discoveryPattern) {
            this.terminal = terminal;
            try {
                this.subject = subject == null ? null : new MultiProtocolURI(subject);
            } catch (MalformedURLException e) {
                this.subject = null;
            }
            this.discoveryPattern = Pattern.compile(discoveryPattern == null ? ".*" : discoveryPattern);
        }
        
        @Override
        public String getTerminal() { return this.terminal; }

        @Override
        public MultiProtocolURI getSubject() { return this.subject; }

        @Override
        public Pattern getDiscoveryPattern() { return this.discoveryPattern; }
    }
    
    public final static String IDENTIFIER = "http://creativecommons.org/ns#";
    public final static String PREFIX = "cc";
    
    private final String predicate;
    private final Set<Literal> literals;

    private CreativeCommons() {
        this.predicate = PREFIX + ":" +  this.name();
        this.literals = null;
    }
    
    private CreativeCommons(Literal[] literals) {
        this.predicate = PREFIX + ":" +  this.name();
        this.literals = new HashSet<Literal>();
        for (Literal l: literals) this.literals.add(l);
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }
    
    @Override
    public Set<Literal> getLiterals() {
        return null;
    }

    @Override
    public String getPredicate() {
        return this.predicate;
    }
}
