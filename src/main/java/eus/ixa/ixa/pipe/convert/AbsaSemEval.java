/*
 *Copyright 2016 Rodrigo Agerri

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package eus.ixa.ixa.pipe.convert;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import eus.ixa.ixa.pipe.ml.StatisticalDocumentClassifier;
import eus.ixa.ixa.pipe.ml.resources.Dictionary;
import eus.ixa.ixa.pipe.ml.tok.RuleBasedTokenizer;
import eus.ixa.ixa.pipe.ml.tok.Token;
import ixa.kaflib.Entity;
import ixa.kaflib.KAFDocument;
import ixa.kaflib.Opinion;
import ixa.kaflib.Term;
import ixa.kaflib.Topic;
import ixa.kaflib.WF;
import opennlp.tools.cmdline.CmdLineUtil;

/**
 * Class for conversors of ABSA SemEval tasks datasets.
 * @author ragerri
 * @version 2016-12-12
 */
public class AbsaSemEval {
  
  //do not instantiate this class
  private AbsaSemEval() {
  }

  private static void absa2015ToNAFNER(KAFDocument kaf, String fileName, String language) {
    //reading the ABSA xml file
    SAXBuilder sax = new SAXBuilder();
    XPathFactory xFactory = XPathFactory.instance();
    try {
      Document doc = sax.build(fileName);
      XPathExpression<Element> expr = xFactory.compile("//sentence",
          Filters.element());
      List<Element> sentences = expr.evaluate(doc);
      
      //naf sentence counter
      int counter = 1;
      for (Element sent : sentences) {
        List<Integer> wfFromOffsets = new ArrayList<>();
        List<Integer> wfToOffsets = new ArrayList<>();
        List<WF> sentWFs = new ArrayList<>();
        List<Term> sentTerms = new ArrayList<>();
        //sentence id and original text
        String sentId = sent.getAttributeValue("id");
        String sentString = sent.getChildText("text");
        //the list contains just one list of tokens
        List<List<Token>> segmentedSentence = tokenizeSentence(sentString, language);
        for (List<Token> sentence : segmentedSentence) {
          for (Token token : sentence) {
            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
                counter);
            wf.setXpath(sentId);
            final List<WF> wfTarget = new ArrayList<WF>();
            wfTarget.add(wf);
            wfFromOffsets.add(wf.getOffset());
            wfToOffsets.add(wf.getOffset() + wf.getLength());
            sentWFs.add(wf);
            Term term = kaf.newTerm(KAFDocument.newWFSpan(wfTarget));
            term.setPos("O");
            term.setLemma(token.getTokenValue());
            sentTerms.add(term);
          }
        }
        counter++;
        String[] tokenIds = new String[sentWFs.size()];
        for (int i = 0; i < sentWFs.size(); i++) {
          tokenIds[i] = sentWFs.get(i).getId();
        }
        //going through every opinion element for each sentence
        //each opinion element can contain one or more opinions
        Element opinionsElement = sent.getChild("Opinions");
        if (opinionsElement != null) {
          //iterating over every opinion in the opinions element
          List<Element> opinionList = opinionsElement.getChildren();
          for (Element opinion : opinionList) {
            String category = opinion.getAttributeValue("category");
            String targetString = opinion.getAttributeValue("target");
            System.err.println("-> " + category + ", " + targetString);
            //adding OTE
            if (!targetString.equalsIgnoreCase("NULL")) {
              int fromOffset = Integer.parseInt(opinion
                    .getAttributeValue("from"));
              int toOffset = Integer.parseInt(opinion
                    .getAttributeValue("to"));
              int startIndex = -1;
              int endIndex = -1;
              for (int i = 0; i < wfFromOffsets.size(); i++) {
                if (wfFromOffsets.get(i) == fromOffset) {
                  startIndex = i;
                }
              }
              for (int i = 0; i < wfToOffsets.size(); i++) {
                if (wfToOffsets.get(i) == toOffset) {
                  //span is +1 with respect to the last token of the span
                  endIndex = i + 1;
                }
              }
              List<String> wfIds = Arrays
                  .asList(Arrays.copyOfRange(tokenIds, startIndex, endIndex));
              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
                references.add(neSpan);
                Entity neEntity = kaf.newEntity(references);
                neEntity.setType(category);
              }
            }
          }
        }
      }//end of sentence
    } catch (JDOMException | IOException e) {
      e.printStackTrace();
    }
  }
  
  private static void absa2015ToNAFNER_TARGET(KAFDocument kaf, String fileName, String language) {
	    //reading the ABSA xml file
	    SAXBuilder sax = new SAXBuilder();
	    XPathFactory xFactory = XPathFactory.instance();
	    try {
	      Document doc = sax.build(fileName);
	      XPathExpression<Element> expr = xFactory.compile("//sentence",
	          Filters.element());
	      List<Element> sentences = expr.evaluate(doc);
	      
	      //naf sentence counter
	      int counter = 1;
	      for (Element sent : sentences) {
	        List<Integer> wfFromOffsets = new ArrayList<>();
	        List<Integer> wfToOffsets = new ArrayList<>();
	        List<WF> sentWFs = new ArrayList<>();
	        List<Term> sentTerms = new ArrayList<>();
	        //sentence id and original text
	        String sentId = sent.getAttributeValue("id");
	        String sentString = sent.getChildText("text");
	        //the list contains just one list of tokens
	        List<List<Token>> segmentedSentence = tokenizeSentence(sentString, language);
	        for (List<Token> sentence : segmentedSentence) {
	          for (Token token : sentence) {
	            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
	                counter);
	            wf.setXpath(sentId);
	            final List<WF> wfTarget = new ArrayList<WF>();
	            wfTarget.add(wf);
	            wfFromOffsets.add(wf.getOffset());
	            wfToOffsets.add(wf.getOffset() + wf.getLength());
	            sentWFs.add(wf);
	            Term term = kaf.newTerm(KAFDocument.newWFSpan(wfTarget));
	            term.setPos("O");
	            term.setLemma(token.getTokenValue());
	            sentTerms.add(term);
	          }
	        }
	        counter++;
	        String[] tokenIds = new String[sentWFs.size()];
	        for (int i = 0; i < sentWFs.size(); i++) {
	          tokenIds[i] = sentWFs.get(i).getId();
	        }
	        //going through every opinion element for each sentence
	        //each opinion element can contain one or more opinions
	        Element opinionsElement = sent.getChild("Opinions");
	        if (opinionsElement != null) {
	          //iterating over every opinion in the opinions element
	          List<Element> opinionList = opinionsElement.getChildren();
	          for (Element opinion : opinionList) {
	            String category = opinion.getAttributeValue("category");
	            String targetString = opinion.getAttributeValue("target");
	            System.err.println("-> " + category + ", " + targetString);
	            //adding OTE
	            if (!targetString.equalsIgnoreCase("NULL")) {
	              int fromOffset = Integer.parseInt(opinion
	                    .getAttributeValue("from"));
	              int toOffset = Integer.parseInt(opinion
	                    .getAttributeValue("to"));
	              int startIndex = -1;
	              int endIndex = -1;
	              for (int i = 0; i < wfFromOffsets.size(); i++) {
	                if (wfFromOffsets.get(i) == fromOffset) {
	                  startIndex = i;
	                }
	              }
	              for (int i = 0; i < wfToOffsets.size(); i++) {
	                if (wfToOffsets.get(i) == toOffset) {
	                  //span is +1 with respect to the last token of the span
	                  endIndex = i + 1;
	                }
	              }
	              List<String> wfIds = Arrays
	                  .asList(Arrays.copyOfRange(tokenIds, startIndex, endIndex));
	              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
	              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
	                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
	                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
	                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
	                references.add(neSpan);
	                Entity neEntity = kaf.newEntity(references);
	                neEntity.setType("TARGET");
	              }
	            }
	          }
	        }
	      }//end of sentence
	    } catch (JDOMException | IOException e) {
	      e.printStackTrace();
	    }
  }
  
  
  private static void absa2015NoTargetToNAFNER(KAFDocument kaf, String fileName, String language, String NullWord) {
	    //reading the ABSA xml file
	    SAXBuilder sax = new SAXBuilder();
	    XPathFactory xFactory = XPathFactory.instance();
	    try {
	      Document doc = sax.build(fileName);
	      XPathExpression<Element> expr = xFactory.compile("//sentence",
	          Filters.element());
	      List<Element> sentences = expr.evaluate(doc);
	      
	      //naf sentence counter
	      int counter = 1;
	      for (Element sent : sentences) {
	        List<Integer> wfFromOffsets = new ArrayList<>();
	        List<Integer> wfToOffsets = new ArrayList<>();
	        List<WF> sentWFs = new ArrayList<>();
	        List<Term> sentTerms = new ArrayList<>();
	        //sentence id and original text
	        String sentId = sent.getAttributeValue("id");
	        String sentString = NullWord + " " + sent.getChildText("text") + " " + NullWord;
	        //String sentString = NullWord + " " + sent.getChildText("text");
	        //the list contains just one list of tokens
	        List<List<Token>> segmentedSentence = tokenizeSentence(sentString, language);
	        for (List<Token> sentence : segmentedSentence) {
	          for (Token token : sentence) {
	            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
	                counter);
	            wf.setXpath(sentId);
	            final List<WF> wfTarget = new ArrayList<WF>();
	            wfTarget.add(wf);
	            wfFromOffsets.add(wf.getOffset());
	            wfToOffsets.add(wf.getOffset() + wf.getLength());
	            sentWFs.add(wf);
	            Term term = kaf.newTerm(KAFDocument.newWFSpan(wfTarget));
	            term.setPos("O");
	            term.setLemma(token.getTokenValue());
	            sentTerms.add(term);
	          }
	        }
	        counter++;
	        String[] tokenIds = new String[sentWFs.size()];
	        for (int i = 0; i < sentWFs.size(); i++) {
	          tokenIds[i] = sentWFs.get(i).getId();
	        }
	        //going through every opinion element for each sentence
	        //each opinion element can contain one or more opinions
	        Element opinionsElement = sent.getChild("Opinions");
	        if (opinionsElement != null) {
	          //iterating over every opinion in the opinions element
	          List<Element> opinionList = opinionsElement.getChildren();
	          for (Element opinion : opinionList) {
	            String category = opinion.getAttributeValue("category");
	            String targetString = opinion.getAttributeValue("target");
	            System.err.println("-> " + category + ", " + targetString);
	            //adding OTE
	            if (!targetString.equalsIgnoreCase("NULL")) {
	              int fromOffset = Integer.parseInt(opinion
	                    .getAttributeValue("from")) + NullWord.length() + 1 ;
	              int toOffset = Integer.parseInt(opinion
	                    .getAttributeValue("to")) + NullWord.length() + 1 ;
	              int startIndex = -1;
	              int endIndex = -1;
	              for (int i = 0; i < wfFromOffsets.size(); i++) {
	                if (wfFromOffsets.get(i) == fromOffset) {
	                  startIndex = i;
	                }
	              }
	              for (int i = 0; i < wfToOffsets.size(); i++) {
	                if (wfToOffsets.get(i) == toOffset) {
	                  //span is +1 with respect to the last token of the span
	                  endIndex = i + 1;
	                }
	              }
	              List<String> wfIds = Arrays
	                  .asList(Arrays.copyOfRange(tokenIds, startIndex, endIndex));
	              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
	              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
	                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
	                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
	                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
	                references.add(neSpan);
	                Entity neEntity = kaf.newEntity(references);
	                neEntity.setType(category);
	              }
	            }
	            else {
	            	List<String> wfIds = Arrays
	  	                  .asList(Arrays.copyOfRange(tokenIds, 0, 1));
	            	List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
	            	if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
		                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
		                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
		                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
		                references.add(neSpan);
		                Entity neEntity = kaf.newEntity(references);
		                neEntity.setType(category);
		              }
	            	wfIds = Arrays
		  	                  .asList(Arrays.copyOfRange(tokenIds, wfFromOffsets.size()-1, wfFromOffsets.size()));
		            wfTermIds = getWFIdsFromTerms(sentTerms);
		            if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
		            	List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
			            ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
			            List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
			            references.add(neSpan);
			            Entity neEntity = kaf.newEntity(references);
			            neEntity.setType(category);
			          }
	            }
	          }
	        }
	      }//end of sentence
	    } catch (JDOMException | IOException e) {
	      e.printStackTrace();
	    }
  }
  
  private static void absa2015TargetNullToNAFNER(KAFDocument kaf, String fileName, String language, String nullDict) {
	    //reading the ABSA xml file
	    SAXBuilder sax = new SAXBuilder();
	    XPathFactory xFactory = XPathFactory.instance();
	    try {
	      Document doc = sax.build(fileName);
	      XPathExpression<Element> expr = xFactory.compile("//sentence",
	          Filters.element());
	      List<Element> sentences = expr.evaluate(doc);
	      
	      //REMOVE UNUSUAL CLASSES
	      List<String> removeClass = new ArrayList<>();
	      removeClass.add("DRINKS#PRICES");
	      removeClass.add("LOCATION#GENERAL");
	      removeClass.add("DRINKS#STYLE_OPTIONS");
	      removeClass.add("DRINKS#QUALITY");
	      removeClass.add("RESTAURANT#PRICES");
	      removeClass.add("FOOD#PRICES");
	      removeClass.add("RESTAURANT#MISCELLANEOUS");
	      
	      //Used opinion target tokens
	      List<String> usedTargets = new ArrayList<>();
	      for (Element sent : sentences) {
	    	  Element opinionsElement = sent.getChild("Opinions");
		        if (opinionsElement != null) {
		        	List<Element> opinionList = opinionsElement.getChildren();
		        	for (Element opinion : opinionList) {
		        		String targetString = opinion.getAttributeValue("target");
			            
			            String[] targetStringSplitted =  targetString.split("\\s+");
			            for (String str : targetStringSplitted) {
			            	if (!usedTargets.contains(str.toLowerCase())) {
			            		usedTargets.add(str.toLowerCase());
			            	}
			            }
		        	}
		        }
	      }
	      
	      //naf sentence counter
	      int counter = 1;
	      for (Element sent : sentences) {
	        List<Integer> wfFromOffsets = new ArrayList<>();
	        List<Integer> wfToOffsets = new ArrayList<>();
	        List<WF> sentWFs = new ArrayList<>();
	        List<Term> sentTerms = new ArrayList<>();
	        //sentence id and original text
	        String sentId = sent.getAttributeValue("id");
	        String sentString = sent.getChildText("text");
	        //the list contains just one list of tokens
	        List<List<Token>> segmentedSentence = tokenizeSentence(sentString, language);
	        for (List<Token> sentence : segmentedSentence) {
	          for (Token token : sentence) {
	            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
	                counter);
	            wf.setXpath(sentId);
	            final List<WF> wfTarget = new ArrayList<WF>();
	            wfTarget.add(wf);
	            wfFromOffsets.add(wf.getOffset());
	            wfToOffsets.add(wf.getOffset() + wf.getLength());
	            sentWFs.add(wf);
	            Term term = kaf.newTerm(KAFDocument.newWFSpan(wfTarget));
	            term.setPos("O");
	            term.setLemma(token.getTokenValue());
	            sentTerms.add(term);
	          }
	        }
	        counter++;
	        String[] tokenIds = new String[sentWFs.size()];
	        for (int i = 0; i < sentWFs.size(); i++) {
	          tokenIds[i] = sentWFs.get(i).getId();
	        }
	        //going through every opinion element for each sentence
	        //each opinion element can contain one or more opinions
	        Element opinionsElement = sent.getChild("Opinions");
	        if (opinionsElement != null) {
	          //iterating over every opinion in the opinions element
	          List<Element> opinionList = opinionsElement.getChildren();
	          for (Element opinion : opinionList) {
	            String category = opinion.getAttributeValue("category");
	            String targetString = opinion.getAttributeValue("target");	            
	            System.err.println("-> " + category + ", " + targetString);
	            //adding OTE
	            if (!targetString.equalsIgnoreCase("NULL")) {
	            	if (!removeClass.contains(category)) {
		              int fromOffset = Integer.parseInt(opinion
		                    .getAttributeValue("from"));
		              int toOffset = Integer.parseInt(opinion
		                    .getAttributeValue("to"));
		              int startIndex = -1;
		              int endIndex = -1;
		              for (int i = 0; i < wfFromOffsets.size(); i++) {
		                if (wfFromOffsets.get(i) == fromOffset) {
		                  startIndex = i;
		                }
		              }
		              for (int i = 0; i < wfToOffsets.size(); i++) {
		                if (wfToOffsets.get(i) == toOffset) {
		                  //span is +1 with respect to the last token of the span
		                  endIndex = i + 1;
		                }
		              }
		              List<String> wfIds = Arrays
		                  .asList(Arrays.copyOfRange(tokenIds, startIndex, endIndex));
		              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
		              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
		                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
		                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
		                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
		                references.add(neSpan);
		                Entity neEntity = kaf.newEntity(references);
		                neEntity.setType(category);
		              }
	            	}
	            }
	            else {
	            	Path path = Paths.get(nullDict);
	            	InputStream in =  CmdLineUtil.openInFile(path.toFile());
	            	Dictionary dictionary = new Dictionary(in);
	            	int startIndex = -1;
	            	List<Integer> startIndexs = new ArrayList<>();
	            	for (WF wf : sentWFs) {
	            		startIndex += 1;
	            		String word = wf.getForm();
	            		if (dictionary.lookup(word.toLowerCase()) != null) {
	            			if (dictionary.lookup(word.toLowerCase()).equalsIgnoreCase(category) && !usedTargets.contains(word.toLowerCase()) && !removeClass.contains(category)) {
	            				startIndexs.add(startIndex);
	            				break;
	            			}
	            		}
	            	}
            		for (Integer indexes : startIndexs) {
            			List<String> wfIds = Arrays
  	      	                  .asList(Arrays.copyOfRange(tokenIds, indexes, indexes+1));
  	      	              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
  	      	              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
  	      	                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
  	      	                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
  	      	                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
  	      	                references.add(neSpan);
  	      	                Entity neEntity = kaf.newEntity(references);
  	      	                neEntity.setType(category);
  	      	              }
            		}
	            		
	            	
	            }
	          }
	        }
	      }//end of sentence
	    } catch (JDOMException | IOException e) {
	      e.printStackTrace();
	    }
	  }
  
  private static void absa2015TargetNullToNAFNER_TARGET(KAFDocument kaf, String fileName, String language, String nullDict) {
	    //reading the ABSA xml file
	    SAXBuilder sax = new SAXBuilder();
	    XPathFactory xFactory = XPathFactory.instance();
	    try {
	      Document doc = sax.build(fileName);
	      XPathExpression<Element> expr = xFactory.compile("//sentence",
	          Filters.element());
	      List<Element> sentences = expr.evaluate(doc);
	      
	      //Used opinion target tokens
	      List<String> usedTargets = new ArrayList<>();
	      for (Element sent : sentences) {
	    	  Element opinionsElement = sent.getChild("Opinions");
		        if (opinionsElement != null) {
		        	List<Element> opinionList = opinionsElement.getChildren();
		        	for (Element opinion : opinionList) {
		        		String targetString = opinion.getAttributeValue("target");
			            
			            String[] targetStringSplitted =  targetString.split("\\s+");
			            for (String str : targetStringSplitted) {
			            	if (!usedTargets.contains(str.toLowerCase())) {
			            		usedTargets.add(str.toLowerCase());
			            	}
			            }
		        	}
		        }
	      }
	      
	      //naf sentence counter
	      int counter = 1;
	      for (Element sent : sentences) {
	        List<Integer> wfFromOffsets = new ArrayList<>();
	        List<Integer> wfToOffsets = new ArrayList<>();
	        List<WF> sentWFs = new ArrayList<>();
	        List<Term> sentTerms = new ArrayList<>();
	        //sentence id and original text
	        String sentId = sent.getAttributeValue("id");
	        String sentString = sent.getChildText("text");
	        //the list contains just one list of tokens
	        List<List<Token>> segmentedSentence = tokenizeSentence(sentString, language);
	        for (List<Token> sentence : segmentedSentence) {
	          for (Token token : sentence) {
	            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
	                counter);
	            wf.setXpath(sentId);
	            final List<WF> wfTarget = new ArrayList<WF>();
	            wfTarget.add(wf);
	            wfFromOffsets.add(wf.getOffset());
	            wfToOffsets.add(wf.getOffset() + wf.getLength());
	            sentWFs.add(wf);
	            Term term = kaf.newTerm(KAFDocument.newWFSpan(wfTarget));
	            term.setPos("O");
	            term.setLemma(token.getTokenValue());
	            sentTerms.add(term);
	          }
	        }
	        counter++;
	        String[] tokenIds = new String[sentWFs.size()];
	        for (int i = 0; i < sentWFs.size(); i++) {
	          tokenIds[i] = sentWFs.get(i).getId();
	        }
	        //going through every opinion element for each sentence
	        //each opinion element can contain one or more opinions
	        Element opinionsElement = sent.getChild("Opinions");
	        if (opinionsElement != null) {
	          //iterating over every opinion in the opinions element
	          List<Element> opinionList = opinionsElement.getChildren();
	          for (Element opinion : opinionList) {
	            String category = opinion.getAttributeValue("category");
	            String targetString = opinion.getAttributeValue("target");	            
	            System.err.println("-> " + category + ", " + targetString);
	            //adding OTE
	            if (!targetString.equalsIgnoreCase("NULL")) {
	              int fromOffset = Integer.parseInt(opinion
	                    .getAttributeValue("from"));
	              int toOffset = Integer.parseInt(opinion
	                    .getAttributeValue("to"));
	              int startIndex = -1;
	              int endIndex = -1;
	              for (int i = 0; i < wfFromOffsets.size(); i++) {
	                if (wfFromOffsets.get(i) == fromOffset) {
	                  startIndex = i;
	                }
	              }
	              for (int i = 0; i < wfToOffsets.size(); i++) {
	                if (wfToOffsets.get(i) == toOffset) {
	                  //span is +1 with respect to the last token of the span
	                  endIndex = i + 1;
	                }
	              }
	              List<String> wfIds = Arrays
	                  .asList(Arrays.copyOfRange(tokenIds, startIndex, endIndex));
	              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
	              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
	                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
	                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
	                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
	                references.add(neSpan);
	                Entity neEntity = kaf.newEntity(references);
	                neEntity.setType("TARGET");
	              }
	            }
	            else {
	            	Path path = Paths.get(nullDict);
	            	InputStream in =  CmdLineUtil.openInFile(path.toFile());
	            	Dictionary dictionary = new Dictionary(in);
	            	int startIndex = -1;
	            	List<Integer> startIndexs = new ArrayList<>();
	            	for (WF wf : sentWFs) {
	            		startIndex += 1;
	            		String word = wf.getForm();
	            		if (dictionary.lookup(word.toLowerCase()) != null) {
	            			if (dictionary.lookup(word.toLowerCase()).equalsIgnoreCase(category) && !usedTargets.contains(word.toLowerCase())) {
	            				startIndexs.add(startIndex);
	            				break;
	            			}
	            		}
	            	}
          		for (Integer indexes : startIndexs) {
          			List<String> wfIds = Arrays
	      	                  .asList(Arrays.copyOfRange(tokenIds, indexes, indexes+1));
	      	              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
	      	              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
	      	                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
	      	                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
	      	                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
	      	                references.add(neSpan);
	      	                Entity neEntity = kaf.newEntity(references);
	      	                neEntity.setType("TARGET");
	      	              }
          		}
	            		
	            	
	            }
	          }
	        }
	      }//end of sentence
	    } catch (JDOMException | IOException e) {
	      e.printStackTrace();
	    }
	  }
  
  private static void absa2015NoTargetAllToNAFNER(KAFDocument kaf, String fileName, String language) {
	    //reading the ABSA xml file
	    SAXBuilder sax = new SAXBuilder();
	    XPathFactory xFactory = XPathFactory.instance();
	    try {
	      Document doc = sax.build(fileName);
	      XPathExpression<Element> expr = xFactory.compile("//sentence",
	          Filters.element());
	      List<Element> sentences = expr.evaluate(doc);
	      
	      //naf sentence counter
	      int counter = 1;
	      for (Element sent : sentences) {
	        List<Integer> wfFromOffsets = new ArrayList<>();
	        List<Integer> wfToOffsets = new ArrayList<>();
	        List<WF> sentWFs = new ArrayList<>();
	        List<Term> sentTerms = new ArrayList<>();
	        //sentence id and original text
	        String sentId = sent.getAttributeValue("id");
	        String sentString = sent.getChildText("text");
	        //the list contains just one list of tokens
	        List<String> NullCategories = new ArrayList<>();
	        Boolean otherTargets = false;
	        List<List<Token>> segmentedSentence = tokenizeSentence(sentString, language);
	        for (List<Token> sentence : segmentedSentence) {
	          for (Token token : sentence) {
	            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
	                counter);
	            wf.setXpath(sentId);
	            final List<WF> wfTarget = new ArrayList<WF>();
	            wfTarget.add(wf);
	            wfFromOffsets.add(wf.getOffset());
	            wfToOffsets.add(wf.getOffset() + wf.getLength());
	            sentWFs.add(wf);
	            Term term = kaf.newTerm(KAFDocument.newWFSpan(wfTarget));
	            term.setPos("O");
	            term.setLemma(token.getTokenValue());
	            sentTerms.add(term);
	          }
	        }
	        counter++;
	        String[] tokenIds = new String[sentWFs.size()];
	        for (int i = 0; i < sentWFs.size(); i++) {
	          tokenIds[i] = sentWFs.get(i).getId();
	        }
	        //going through every opinion element for each sentence
	        //each opinion element can contain one or more opinions
	        Element opinionsElement = sent.getChild("Opinions");
	        if (opinionsElement != null) {
	          //iterating over every opinion in the opinions element
	          List<Element> opinionList = opinionsElement.getChildren();
	          for (Element opinion : opinionList) {
	            String category = opinion.getAttributeValue("category");
	            String targetString = opinion.getAttributeValue("target");
	            System.err.println("-> " + category + ", " + targetString);
	            //adding OTE
	            if (!targetString.equalsIgnoreCase("NULL")) {
	              otherTargets = true;
	              int fromOffset = Integer.parseInt(opinion
	                    .getAttributeValue("from"));
	              int toOffset = Integer.parseInt(opinion
	                    .getAttributeValue("to"));
	              int startIndex = -1;
	              int endIndex = -1;
	              for (int i = 0; i < wfFromOffsets.size(); i++) {
	                if (wfFromOffsets.get(i) == fromOffset) {
	                  startIndex = i;
	                }
	              }
	              for (int i = 0; i < wfToOffsets.size(); i++) {
	                if (wfToOffsets.get(i) == toOffset) {
	                  //span is +1 with respect to the last token of the span
	                  endIndex = i + 1;
	                }
	              }
	              List<String> wfIds = Arrays
	                  .asList(Arrays.copyOfRange(tokenIds, startIndex, endIndex));
	              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
	              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
	                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
	                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
	                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
	                references.add(neSpan);
	                Entity neEntity = kaf.newEntity(references);
	                neEntity.setType(category);
	              }
	            }
		        else {
		        	NullCategories.add(category);
		        }
	          }
	          
	          for (int y = 0; y < NullCategories.size(); y++) {
	        	  
	        	if (otherTargets) {
	        	wfFromOffsets = new ArrayList<>();
	  	        wfToOffsets = new ArrayList<>();
	  	        sentWFs = new ArrayList<>();
	  	        sentTerms = new ArrayList<>();
	        	  for (List<Token> sentence : segmentedSentence) {
	    	          for (Token token : sentence) {
	    	            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
	    	                counter);
	    	            wf.setXpath(sentId);
	    	            final List<WF> wfTarget = new ArrayList<WF>();
	    	            wfTarget.add(wf);
	    	            wfFromOffsets.add(wf.getOffset());
	    	            wfToOffsets.add(wf.getOffset() + wf.getLength());
	    	            sentWFs.add(wf);
	    	            Term term = kaf.newTerm(KAFDocument.newWFSpan(wfTarget));
	    	            term.setPos("O");
	    	            term.setLemma(token.getTokenValue());
	    	            sentTerms.add(term);
	    	          }
	    	        }
	        	  counter++;
	        	  tokenIds = new String[sentWFs.size()];
	        	  for (int i = 0; i < sentWFs.size(); i++) {
	        		  tokenIds[i] = sentWFs.get(i).getId();
	  	          }
	        	  System.err.println(sentString);
	        	 
	        	}
	              //System.err.println(fromOffset + "-" + toOffset + "-" + startIndex + "-" + endIndex);
	        	otherTargets=true;
	              List<String> wfIds = Arrays
	                  .asList(Arrays.copyOfRange(tokenIds, 0, tokenIds.length));
	              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
	              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
	                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
	                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
	                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
	                references.add(neSpan);
	                Entity neEntity = kaf.newEntity(references);
	                neEntity.setType(NullCategories.get(y));
	              }
	        	  
	        	  
	          }
	        }
	      }//end of sentence
	    } catch (JDOMException | IOException e) {
	      e.printStackTrace();
	    }  
  }
  
  public static String absa2015ToCoNLL2002(String fileName, String language) {
    KAFDocument kaf = new KAFDocument("en", "v1.naf");
    absa2015ToNAFNER(kaf, fileName, language);
    String conllFile = ConllUtils.nafToCoNLLConvert2002(kaf);
    return conllFile;
  }
  
  public static String absa2015ToCoNLL2002_TARGET(String fileName, String language) {
	    KAFDocument kaf = new KAFDocument("en", "v1.naf");
	    absa2015ToNAFNER_TARGET(kaf, fileName, language);
	    String conllFile = ConllUtils.nafToCoNLLConvert2002(kaf);
	    return conllFile;
	  }
  
  public static String absa2015NoTargetToCoNLL2002(String fileName, String language, String NullWord) {
	    KAFDocument kaf = new KAFDocument("en", "v1.naf");
	    absa2015NoTargetToNAFNER(kaf, fileName, language, NullWord);
	    //System.err.println(kaf.toString());
	    String conllFile = ConllUtils.nafToCoNLLConvert2002(kaf);
	    return conllFile;
  }
  
  public static String absa2015TargetNullToCoNLL2002(String fileName, String language, String nullDict) {
	    KAFDocument kaf = new KAFDocument("en", "v1.naf");
	    absa2015TargetNullToNAFNER(kaf, fileName, language, nullDict);
	    //System.err.println(kaf.toString());
	    String conllFile = ConllUtils.nafToCoNLLConvert2002(kaf);
	    return conllFile;
	}
  
  public static String absa2015TargetNullToCoNLL2002_TARGET(String fileName, String language, String nullDict) {
	    KAFDocument kaf = new KAFDocument("en", "v1.naf");
	    absa2015TargetNullToNAFNER_TARGET(kaf, fileName, language, nullDict);
	    //System.err.println(kaf.toString());
	    String conllFile = ConllUtils.nafToCoNLLConvert2002(kaf);
	    return conllFile;
	}
  
  public static String absa2015NoTargetAllToCoNLL2002(String fileName, String language) {
	    KAFDocument kaf = new KAFDocument("en", "v1.naf");
	    absa2015NoTargetAllToNAFNER(kaf, fileName, language);
	    //System.err.println(kaf.toString());
	    String conllFile = ConllUtils.nafToCoNLLConvert2002(kaf);
	    return conllFile;
  }
  
  /**
   * Get all the WF ids for the terms contained in the KAFDocument.
   * @param kaf the KAFDocument
   * @return the list of all WF ids in the terms layer
   */
  private static List<String> getWFIdsFromTerms(List<Term> terms) {
    List<String> wfTermIds = new ArrayList<>();
    for (int i = 0; i < terms.size(); i++) {
      List<WF> sentTerms = terms.get(i).getWFs();
      for (WF form : sentTerms) {
        wfTermIds.add(form.getId());
      }
    }
    return wfTermIds;
  }
  
  /**
   * Check that the references from the entity spans are
   * actually contained in the term ids.
   * @param wfIds the worform ids corresponding to the Term span
   * @param termWfIds all the terms in the document
   * @return true or false
   */
  private static boolean checkTermsRefsIntegrity(List<String> wfIds,
      List<String> termWfIds) {
    for (int i = 0; i < wfIds.size(); i++) {
      if (!termWfIds.contains(wfIds.get(i))) {
        return false;
      }
    }
    return true;
  }

  public static String absa2015ToWFs(String fileName, String language) {
    KAFDocument kaf = new KAFDocument("en", "v1.naf");
    SAXBuilder sax = new SAXBuilder();
    XPathFactory xFactory = XPathFactory.instance();
    try {
      Document doc = sax.build(fileName);
      XPathExpression<Element> expr = xFactory.compile("//sentence",
          Filters.element());
      List<Element> sentences = expr.evaluate(doc);

      int counter = 1;
      for (Element sent : sentences) {
        String sentId = sent.getAttributeValue("id");
        String sentString = sent.getChildText("text");
        List<List<Token>> segmentedSentences = tokenizeSentence(sentString, language);
        for (List<Token> sentence : segmentedSentences) {
          for (Token token : sentence) {
            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
                counter);
            wf.setXpath(sentId);
          }
        }
        counter++;
      }
    } catch (JDOMException | IOException e) {
      e.printStackTrace();
    }
    return kaf.toString();
  }
  
  public static String absa2015NoTargetToWFs(String fileName, String language, String NullWord) {
	    KAFDocument kaf = new KAFDocument("en", "v1.naf");
	    SAXBuilder sax = new SAXBuilder();
	    XPathFactory xFactory = XPathFactory.instance();
	    try {
	      Document doc = sax.build(fileName);
	      XPathExpression<Element> expr = xFactory.compile("//sentence",
	          Filters.element());
	      List<Element> sentences = expr.evaluate(doc);

	      int counter = 1;
	      for (Element sent : sentences) {
	        String sentId = sent.getAttributeValue("id");
	        String sentString = NullWord + " " + sent.getChildText("text") + " " + NullWord;
	        //String sentString = NullWord + " " + sent.getChildText("text");
	        List<List<Token>> segmentedSentences = tokenizeSentence(sentString, language);
	        for (List<Token> sentence : segmentedSentences) {
	          for (Token token : sentence) {
	            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
	                counter);
	            wf.setXpath(sentId);
	          }
	        }
	        counter++;
	      }
	    } catch (JDOMException | IOException e) {
	      e.printStackTrace();
	    }
	    return kaf.toString();
	  }

  public static String nafToAbsa2015(String inputNAF) throws IOException {

    Path kafPath = Paths.get(inputNAF);
    KAFDocument kaf = KAFDocument.createFromFile(kafPath.toFile());
    Set<String> reviewIds = getReviewIdsFromXpathAttribute(kaf);
        
    //root element in ABSA 2015 and 2016 format
    Element reviewsElem = new Element("Reviews");
    Document doc = new Document(reviewsElem);
    
    //creating Reviews children of Review
    for (String reviewId : reviewIds) {
      Element reviewElem = new Element("Review");
      reviewElem.setAttribute("rid", reviewId);
      Element sentencesElem = new Element("sentences");
      //getting the sentences in the review
      List<List<WF>> sentencesByReview = getSentencesByReview(kaf, reviewId);
      for (List<WF> sent : sentencesByReview) {
        String sentId = sent.get(0).getXpath();
        Integer sentNumber = sent.get(0).getSent();
        
        //getting text element from word forms in NAF
        String textString = getSentenceStringFromWFs(sent);
        Element sentenceElem = new Element("sentence");
        sentenceElem.setAttribute("id", sentId);
        Element textElem = new Element("text");
        textElem.setText(textString);
        sentenceElem.addContent(textElem);
        
        //creating opinions element for sentence
        List<Opinion> opinionsBySentence = getOpinionsBySentence(kaf, sentNumber);
        Element opinionsElem = new Element("Opinions");
        if (!opinionsBySentence.isEmpty()) {
          //getting opinion info from NAF Opinion layer
          for (Opinion opinion : opinionsBySentence) {
            Element opinionElem = new Element("Opinion");
            //String polarity = opinion.getOpinionExpression().getPolarity();
            String category = opinion.getOpinionExpression().getSentimentProductFeature();
            String targetString = opinion.getStr();
            int fromOffset = opinion.getOpinionExpression().getTerms().get(0).getWFs().get(0).getOffset();
            List<WF> targetWFs = opinion.getOpinionExpression().getTerms().get(opinion.getOpinionExpression().getTerms().size() -1).getWFs();
            int toOffset = targetWFs.get(targetWFs.size() -1).getOffset() + targetWFs.get(targetWFs.size() -1).getLength();
            opinionElem.setAttribute("target", targetString);
            opinionElem.setAttribute("category", category);
            //TODO we still do not have polarity here
            opinionElem.setAttribute("polarity", "na");
            opinionElem.setAttribute("from", Integer.toString(fromOffset));
            opinionElem.setAttribute("to", Integer.toString(toOffset));
            opinionsElem.addContent(opinionElem);
          }
        }
        sentenceElem.addContent(opinionsElem);
        sentencesElem.addContent(sentenceElem);
      }
      reviewElem.addContent(sentencesElem);
      reviewsElem.addContent(reviewElem);
    }//end of review
    
    XMLOutputter xmlOutput = new XMLOutputter();
    Format format = Format.getPrettyFormat();
    xmlOutput.setFormat(format);
    return xmlOutput.outputString(doc);
  }
  
  public static String nafNoTargetToAbsa2015(String inputNAF, String NullWord) throws IOException {

	    Path kafPath = Paths.get(inputNAF);
	    KAFDocument kaf = KAFDocument.createFromFile(kafPath.toFile());
	    Set<String> reviewIds = getReviewIdsFromXpathAttribute(kaf);
	        
	    //root element in ABSA 2015 and 2016 format
	    Element reviewsElem = new Element("Reviews");
	    Document doc = new Document(reviewsElem);
	    
	    //creating Reviews children of Review
	    for (String reviewId : reviewIds) {
	      Element reviewElem = new Element("Review");
	      reviewElem.setAttribute("rid", reviewId);
	      Element sentencesElem = new Element("sentences");
	      //getting the sentences in the review
	      List<List<WF>> sentencesByReview = getSentencesByReview(kaf, reviewId);
	      for (List<WF> sent : sentencesByReview) {
	        String sentId = sent.get(0).getXpath();
	        Integer sentNumber = sent.get(0).getSent();
	        
	        //getting text element from word forms in NAF
	        String textString = getSentenceStringFromWFs(sent);
	        textString = textString.substring(NullWord.length(),textString.length());
	        textString = textString.substring(0,textString.length() - NullWord.length());
	        Element sentenceElem = new Element("sentence");
	        sentenceElem.setAttribute("id", sentId);
	        Element textElem = new Element("text");
	        textElem.setText(textString);
	        sentenceElem.addContent(textElem);
	        
	        //creating opinions element for sentence
	        List<Opinion> opinionsBySentence = getOpinionsBySentence(kaf, sentNumber);
	        Element opinionsElem = new Element("Opinions");
	        if (!opinionsBySentence.isEmpty()) {
	          //getting opinion info from NAF Opinion layer
	          for (Opinion opinion : opinionsBySentence) {
	            Element opinionElem = new Element("Opinion");
	            //String polarity = opinion.getOpinionExpression().getPolarity();
	            String category = opinion.getOpinionExpression().getSentimentProductFeature();
	            String targetString = opinion.getStr();
	            int fromOffset = 0;
	            int toOffset = 0;
	            if (!targetString.equals(NullWord)) {
	            	fromOffset = opinion.getOpinionExpression().getTerms().get(0).getWFs().get(0).getOffset() - (NullWord.length() + 1);
		            List<WF> targetWFs = opinion.getOpinionExpression().getTerms().get(opinion.getOpinionExpression().getTerms().size() -1).getWFs();
		            toOffset = targetWFs.get(targetWFs.size() -1).getOffset() + targetWFs.get(targetWFs.size() -1).getLength() - (NullWord.length() + 1);
	            }
	            else {
	            	targetString="NULL";
	            }
	            opinionElem.setAttribute("target", targetString);
	            opinionElem.setAttribute("category", category);
	            //TODO we still do not have polarity here
	            opinionElem.setAttribute("polarity", "na");
	            opinionElem.setAttribute("from", Integer.toString(fromOffset));
	            opinionElem.setAttribute("to", Integer.toString(toOffset));
	            opinionsElem.addContent(opinionElem);
	          }
	        }
	        sentenceElem.addContent(opinionsElem);
	        sentencesElem.addContent(sentenceElem);
	      }
	      reviewElem.addContent(sentencesElem);
	      reviewsElem.addContent(reviewElem);
	    }//end of review
	    
	    XMLOutputter xmlOutput = new XMLOutputter();
	    Format format = Format.getPrettyFormat();
	    xmlOutput.setFormat(format);
	    return xmlOutput.outputString(doc);
  }
  
  public static String nafNoTargetAllToAbsa2015(String inputNAF) throws IOException {

	  Path kafPath = Paths.get(inputNAF);
	    KAFDocument kaf = KAFDocument.createFromFile(kafPath.toFile());
	    Set<String> reviewIds = getReviewIdsFromXpathAttribute(kaf);
	        
	    //root element in ABSA 2015 and 2016 format
	    Element reviewsElem = new Element("Reviews");
	    Document doc = new Document(reviewsElem);
	    
	    //creating Reviews children of Review
	    for (String reviewId : reviewIds) {
	      Element reviewElem = new Element("Review");
	      reviewElem.setAttribute("rid", reviewId);
	      Element sentencesElem = new Element("sentences");
	      //getting the sentences in the review
	      List<List<WF>> sentencesByReview = getSentencesByReview(kaf, reviewId);
	      for (List<WF> sent : sentencesByReview) {
	        String sentId = sent.get(0).getXpath();
	        Integer sentNumber = sent.get(0).getSent();
	        
	        //getting text element from word forms in NAF
	        String textString = getSentenceStringFromWFs(sent);
	        Element sentenceElem = new Element("sentence");
	        sentenceElem.setAttribute("id", sentId);
	        Element textElem = new Element("text");
	        textElem.setText(textString);
	        sentenceElem.addContent(textElem);
	        
	        //creating opinions element for sentence
	        List<Opinion> opinionsBySentence = getOpinionsBySentence(kaf, sentNumber);
	        Element opinionsElem = new Element("Opinions");
	        if (!opinionsBySentence.isEmpty()) {
	          //getting opinion info from NAF Opinion layer
	          for (Opinion opinion : opinionsBySentence) {
	            Element opinionElem = new Element("Opinion");
	            //String polarity = opinion.getOpinionExpression().getPolarity();
	            String category = opinion.getOpinionExpression().getSentimentProductFeature();
	            String targetString = opinion.getStr();
	            int fromOffset = opinion.getOpinionExpression().getTerms().get(0).getWFs().get(0).getOffset();
	            List<WF> targetWFs = opinion.getOpinionExpression().getTerms().get(opinion.getOpinionExpression().getTerms().size() -1).getWFs();
	            int toOffset = targetWFs.get(targetWFs.size() -1).getOffset() + targetWFs.get(targetWFs.size() -1).getLength();
	            if (targetString.equals(textString)) {
	            	targetString = "NULL";
	            	fromOffset = 0;
	            	toOffset = 0;
	            }
	            opinionElem.setAttribute("target", targetString);
	            opinionElem.setAttribute("category", category);
	            //TODO we still do not have polarity here
	            opinionElem.setAttribute("polarity", "na");
	            opinionElem.setAttribute("from", Integer.toString(fromOffset));
	            opinionElem.setAttribute("to", Integer.toString(toOffset));
	            opinionsElem.addContent(opinionElem);
	          }
	        }
	        sentenceElem.addContent(opinionsElem);
	        sentencesElem.addContent(sentenceElem);
	      }
	      reviewElem.addContent(sentencesElem);
	      reviewsElem.addContent(reviewElem);
	    }//end of review
	    
	    XMLOutputter xmlOutput = new XMLOutputter();
	    Format format = Format.getPrettyFormat();
	    xmlOutput.setFormat(format);
	    return xmlOutput.outputString(doc);
}
  
  private static List<List<WF>> getSentencesByReview(KAFDocument kaf, String reviewId) {
    List<List<WF>> sentsByReview = new ArrayList<List<WF>>();
    for (List<WF> sent : kaf.getSentences()) {
      if (sent.get(0).getXpath().split(":")[0].equalsIgnoreCase(reviewId)) {
        sentsByReview.add(sent);
      }
    }
    return sentsByReview;
  }
  
  private static List<Opinion> getOpinionsBySentence(KAFDocument kaf, Integer sentNumber) {
    List<Opinion> opinionList = kaf.getOpinions();
    List<Opinion> opinionsBySentence = new ArrayList<>();
    for (Opinion opinion : opinionList) {
      if (sentNumber.equals(opinion.getOpinionExpression().getSpan().getFirstTarget().getSent())) {
        opinionsBySentence.add(opinion);
      }
    }
    return opinionsBySentence;
  }
  
  private static Set<String> getReviewIdsFromXpathAttribute(KAFDocument kaf) {
    Set<String> reviewIds = new LinkedHashSet<>();
    for (List<WF> sent : kaf.getSentences()) {
      String reviewId = sent.get(0).getXpath().split(":")[0];
      reviewIds.add(reviewId);
    }
    return reviewIds;
  }
  
  private static String getSentenceStringFromWFs(List<WF> sent) {
    StringBuilder sb = new StringBuilder();
    for (WF wf : sent) {
      sb.append(wf.getForm()).append(" ");
    }
    return sb.toString().trim();
  }

  private static void absa2014ToNAFNER(KAFDocument kaf, String fileName, String language) {
    //reading the ABSA xml file
    SAXBuilder sax = new SAXBuilder();
    XPathFactory xFactory = XPathFactory.instance();
    try {
      Document doc = sax.build(fileName);
      XPathExpression<Element> expr = xFactory.compile("//sentence",
          Filters.element());
      List<Element> sentences = expr.evaluate(doc);
      
      //naf sentence counter
      int counter = 1;
      for (Element sent : sentences) {
        List<Integer> wfFromOffsets = new ArrayList<>();
        List<Integer> wfToOffsets = new ArrayList<>();
        List<WF> sentWFs = new ArrayList<>();
        List<Term> sentTerms = new ArrayList<>();
        //sentence id and original text
        String sentId = sent.getAttributeValue("id");
        String sentString = sent.getChildText("text");
        //the list contains just one list of tokens
        List<List<Token>> segmentedSentence = tokenizeSentence(sentString, language);
        for (List<Token> sentence : segmentedSentence) {
          for (Token token : sentence) {
            WF wf = kaf.newWF(token.startOffset(), token.getTokenValue(),
                counter);
            wf.setXpath(sentId);
            final List<WF> wfTarget = new ArrayList<WF>();
            wfTarget.add(wf);
            wfFromOffsets.add(wf.getOffset());
            wfToOffsets.add(wf.getOffset() + wf.getLength());
            sentWFs.add(wf);
            Term term = kaf.newTerm(KAFDocument.newWFSpan(wfTarget));
            term.setPos("O");
            term.setLemma(token.getTokenValue());
            sentTerms.add(term);
          }
        }
        counter++;
        String[] tokenIds = new String[sentWFs.size()];
        for (int i = 0; i < sentWFs.size(); i++) {
          tokenIds[i] = sentWFs.get(i).getId();
        }
        //going through every opinion element for each sentence
        //each opinion element can contain one or more opinions
        Element aspectTermsElem = sent.getChild("aspectTerms");
        
        if (aspectTermsElem != null) {
          
          List<Element> aspectTermsList = aspectTermsElem.getChildren();
          //iterating over every opinion in the opinions element
          if (!aspectTermsList.isEmpty()) {
          for (Element aspectTerm : aspectTermsList) {
            String targetString = aspectTerm.getAttributeValue("term");
            System.err.println("-> " + targetString);
            //adding OTE
              int fromOffset = Integer.parseInt(aspectTerm
                    .getAttributeValue("from"));
              int toOffset = Integer.parseInt(aspectTerm
                    .getAttributeValue("to"));
              int startIndex = -1;
              int endIndex = -1;
              for (int i = 0; i < wfFromOffsets.size(); i++) {
                if (wfFromOffsets.get(i) == fromOffset) {
                  startIndex = i;
                }
              }
              for (int i = 0; i < wfToOffsets.size(); i++) {
                if (wfToOffsets.get(i) == toOffset) {
                  //span is +1 with respect to the last token of the span
                  endIndex = i + 1;
                }
              }
              List<String> wfIds = Arrays
                  .asList(Arrays.copyOfRange(tokenIds, startIndex, endIndex));
              List<String> wfTermIds = getWFIdsFromTerms(sentTerms);
              if (checkTermsRefsIntegrity(wfIds, wfTermIds)) {
                List<Term> nameTerms = kaf.getTermsFromWFs(wfIds);
                ixa.kaflib.Span<Term> neSpan = KAFDocument.newTermSpan(nameTerms);
                List<ixa.kaflib.Span<Term>> references = new ArrayList<ixa.kaflib.Span<Term>>();
                references.add(neSpan);
                Entity neEntity = kaf.newEntity(references);
                neEntity.setType("term");
              }
          }
          }
        }
      }//end of sentence
    } catch (JDOMException | IOException e) {
      e.printStackTrace();
    }
  }
  
  public static String absa2014ToCoNLL2002(String fileName, String language) {
    KAFDocument kaf = new KAFDocument("en", "v1.naf");
    absa2014ToNAFNER(kaf, fileName, language);
    String conllFile = ConllUtils.nafToCoNLLConvert2002(kaf);
    return conllFile;
  }
  
  public static String nafToAbsa2014(String kafDocument) {

    KAFDocument kaf = null;
    try {
      Path kafPath = Paths.get(kafDocument);
      kaf = KAFDocument.createFromFile(kafPath.toFile());
    } catch (IOException e) {
      e.printStackTrace();
    }
    Element sentencesElem = new Element("sentences");
    Document doc = new Document(sentencesElem);

    for (List<WF> sent : kaf.getSentences()) {
      String sentId = sent.get(0).getXpath();
      Integer sentNumber = sent.get(0).getSent();
      
      //getting text element from WFs in NAF
      String textString = getSentenceStringFromWFs(sent);
      Element sentenceElem = new Element("sentence");
      sentenceElem.setAttribute("id", sentId);
      Element textElem = new Element("text");
      textElem.setText(textString);
      sentenceElem.addContent(textElem);
      
      //creating opinions element for sentence
      List<Opinion> opinionsBySentence = getOpinionsBySentence(kaf, sentNumber);
      if (!opinionsBySentence.isEmpty()) {
        Element aspectTerms = new Element("aspectTerms");
        //getting opinion info from NAF Opinion layer
        for (Opinion opinion : opinionsBySentence) {
          String polarity = "";
          String targetString = opinion.getStr();
          int fromOffset = opinion.getOpinionExpression().getTerms().get(0).getWFs().get(0).getOffset();
          List<WF> targetWFs = opinion.getOpinionExpression().getTerms().get(opinion.getOpinionExpression().getTerms().size() -1).getWFs();
          int toOffset = targetWFs.get(targetWFs.size() -1).getOffset() + targetWFs.get(targetWFs.size() -1).getLength();
          
          Element aspectTerm = new Element("aspectTerm");
          aspectTerm.setAttribute("term", targetString);
          aspectTerm.setAttribute("polarity", polarity);
          aspectTerm.setAttribute("from", Integer.toString(fromOffset));
          aspectTerm.setAttribute("to", Integer.toString(toOffset));
          aspectTerms.addContent(aspectTerm);
        }
        sentenceElem.addContent(aspectTerms);
      }
      sentencesElem.addContent(sentenceElem);
    }
    XMLOutputter xmlOutput = new XMLOutputter();
    Format format = Format.getPrettyFormat();
    xmlOutput.setFormat(format);
    return xmlOutput.outputString(doc);
  }
  
  public static void getYelpText(String fileName) throws IOException {
    JSONParser parser = new JSONParser();
    Path filePath = Paths.get(fileName);
    BufferedReader breader = new BufferedReader(Files.newBufferedReader(filePath, StandardCharsets.UTF_8));
    String line;
    while ((line = breader.readLine()) != null) {
      try {
        Object obj = parser.parse(line);
        JSONObject jsonObject = (JSONObject) obj;
        String text = (String) jsonObject.get("text");
        System.out.println(text);
      } catch (ParseException e) {
        e.printStackTrace();
      }
    }
    breader.close();
  }
  
  private static List<List<Token>> tokenizeSentence(String sentString, String language) {
    RuleBasedTokenizer tokenizer = new RuleBasedTokenizer(sentString,
        setTokenizeProperties(language));
    List<String> sentenceList = new ArrayList<>();
    sentenceList.add(sentString);
    String[] sentences = sentenceList.toArray(new String[sentenceList.size()]);
    List<List<Token>> tokens = tokenizer.tokenize(sentences);
    return tokens;
  }

  private static Properties setTokenizeProperties(String language) {
    Properties annotateProperties = new Properties();
    annotateProperties.setProperty("language", language);
    annotateProperties.setProperty("normalize", "default");
    annotateProperties.setProperty("hardParagraph", "no");
    annotateProperties.setProperty("untokenizable", "no");
    return annotateProperties;
  }

  public static String getStringFromTokens(String sentString, String language) {

    StringBuilder sb = new StringBuilder();
    List<List<Token>> tokens = tokenizeSentence(sentString, language);
    for (List<Token> sentence : tokens) {
      for (Token tok : sentence) {
        sb.append(tok.getTokenValue()).append(" ");
      }
    }
    return sb.toString();
  }
  
  public static String absa2015Toabsa2015NoNullTarget(String fileName) {
    //reading the ABSA xml file
    SAXBuilder sax = new SAXBuilder();
    XPathFactory xFactory = XPathFactory.instance();
    Document doc = null;
    try {
      doc = sax.build(fileName);
      XPathExpression<Element> expr = xFactory.compile("//sentence",
          Filters.element());
      List<Element> sentences = expr.evaluate(doc);

      for (Element sent : sentences) {
        Element opinionsElement = sent.getChild("Opinions");
        if (opinionsElement != null) {
          //iterating over every opinion in the opinions element
          List<Element> opinionList = opinionsElement.getChildren();
          for (Element opinion : opinionList) {
            String targetString = opinion.getAttributeValue("target");
            //adding OTE
            if (targetString.equalsIgnoreCase("NULL")) {
            	sent.getParent().removeContent(sent);
            	break;
            }
          }
        }
      }//end of sentence
    } catch (JDOMException | IOException e) {
      e.printStackTrace();
    }
    
    XMLOutputter xmlOutput = new XMLOutputter();
    Format format = Format.getPrettyFormat();
    xmlOutput.setFormat(format);
    return xmlOutput.outputString(doc);
  }
  
  public static String absa2015ToTextFilebyAspect(String fileName, String Aspect, String OnlyOne, String Nulls) {
	SAXBuilder sax = new SAXBuilder();
    XPathFactory xFactory = XPathFactory.instance();
    Document doc = null;
    String text = "";
    Integer total = 0;
    Integer totalAspect = 0;
    Double Null = 0.0;
    Double noNull = 0.0;
    try {
      doc = sax.build(fileName);
      XPathExpression<Element> expr = xFactory.compile("//sentence",
          Filters.element());
      List<Element> sentences = expr.evaluate(doc);

      for (Element sent : sentences) {
    	total += 1;
        Element opinionsElement = sent.getChild("Opinions");
        if (opinionsElement != null) {
          //iterating over every opinion in the opinions element
          List<Element> opinionList = opinionsElement.getChildren();
          
          if ((OnlyOne.equalsIgnoreCase("yes") && opinionList.size()==1) || OnlyOne.equalsIgnoreCase("no")) {
        	  for (Element opinion : opinionList) {
                  String targetString = opinion.getAttributeValue("target");
                  String categoryString = opinion.getAttributeValue("category");
                  //adding OTE
                  if (categoryString.equalsIgnoreCase(Aspect)) {
                	if ((targetString.equalsIgnoreCase("NULL") && Nulls.equalsIgnoreCase("yes")) || Nulls.equalsIgnoreCase("no")) {
                      	totalAspect += 1;
                      	noNull +=1;
                      	text += sent.getChildText("text") + "\n";
                	}
                  	if (targetString.equalsIgnoreCase("NULL")) {
                  		noNull -= 1;
                  		Null += 1;
                  	}
                  	break;
                  }
        	  }
          
          }
        }
      }//end of sentence
    } catch (JDOMException | IOException e) {
      e.printStackTrace();
    }
    
    System.err.println("Aspect: " + Aspect);
    System.err.println("Total Sentences: " + total);
    System.err.println("With the given aspect: " + totalAspect);
    System.err.println("With the given aspect and NULL: " + Null + " - " + Null/totalAspect );
    System.err.println("With the given aspect and not NULL: " + noNull + " - " + noNull/totalAspect );
    return text;
  }
  
  public static String absa2015ToDocCatFormat(String fileName, String language) {
	SAXBuilder sax = new SAXBuilder();
    XPathFactory xFactory = XPathFactory.instance();
    Document doc = null;
    String text = "";
    
    try {
      doc = sax.build(fileName);
      XPathExpression<Element> expr = xFactory.compile("//sentence",
          Filters.element());
      List<Element> sentences = expr.evaluate(doc);

      /*
      List<String> removeAspects = new ArrayList<>();
      removeAspects.add("DRINKS#PRICES");
      removeAspects.add("LOCATION#GENERAL");
      removeAspects.add("DRINKS#STYLE_OPTIONS");
      removeAspects.add("DRINKS#QUALITY");
      */
      
      for (Element sent : sentences) {
        Element opinionsElement = sent.getChild("Opinions");
        String sentStringTmp = sent.getChildText("text");
        String sentString = "";
        List<List<Token>> segmentedSentence = tokenizeSentence(sentStringTmp, language);
        for (List<Token> sentence : segmentedSentence) {
          for (Token token : sentence) {
        	  sentString += token.getTokenValue() + " ";
          }
        }
        
        List<String> removeAspects = new ArrayList<>();
        
        if (opinionsElement != null) {
          //iterating over every opinion in the opinions element
          List<Element> opinionList = opinionsElement.getChildren();

    	  for (Element opinion : opinionList) {

              //String targetString = opinion.getAttributeValue("target");
              String categoryString = opinion.getAttributeValue("category");
    		  //if(!removeAspects.contains(categoryString)) {
                  //text += categoryString + "\t" + sentString + "\t" + targetString +"\n";
    			  removeAspects.add(categoryString);
    			  
    		  //}
    	  }
    	  
    	  if (removeAspects.contains("FOOD#QUALITY")) {
    		  text += "FOOD#QUALITY\t" + sentString + "\n";
    	  } else if (removeAspects.contains("SERVICE#GENERAL")) {
    		  text += "SERVICE#GENERAL\t" + sentString + "\n";
    	  } else if (removeAspects.contains("RESTAURANT#GENERAL")) {
    		  text += "RESTAURANT#GENERAL\t" + sentString + "\n";
    	  } else if (removeAspects.contains("AMBIENCE#GENERAL")) {
    		  text += "AMBIENCE#GENERAL\t" + sentString + "\n";
    	  } else if (removeAspects.contains("FOOD#STYLE_OPTIONS")) {
    		  text += "FOOD#STYLE_OPTIONS\t" + sentString + "\n";
    	  } else if (removeAspects.contains("RESTAURANT#MISCELLANEOUS")) {
    		  text += "RESTAURANT#MISCELLANEOUS\t" + sentString + "\n";
    	  }else if (removeAspects.contains("FOOD#PRICES")) {
    		  text += "FOOD#PRICES\t" + sentString + "\n";
    	  }else if (removeAspects.contains("RESTAURANT#PRICES")) {
    		  text += "RESTAURANT#PRICES\t" + sentString + "\n";
    	  }else if (removeAspects.contains("DRINKS#QUALITY")) {
    		  text += "DRINKS#QUALITY\t" + sentString + "\n";
    	  }else if (removeAspects.contains("DRINKS#STYLE_OPTIONS")) {
    		  text += "DRINKS#STYLE_OPTIONS\t" + sentString + "\n";
    	  }else if (removeAspects.contains("LOCATION#GENERAL")) {
    		  text += "LOCATION#GENERAL\t" + sentString + "\n";
    	  }else if (removeAspects.contains("DRINKS#PRICES")) {
    		  text += "DRINKS#PRICES\t" + sentString + "\n";
    	  }
          
          
        }
      }//end of sentence
    } catch (JDOMException | IOException e) {
      e.printStackTrace();
    }
    
    return text;
  }
  
  public static String absa2015ToDocCatFormatByAspect(String fileName, String language, String aspect) {
		SAXBuilder sax = new SAXBuilder();
	    XPathFactory xFactory = XPathFactory.instance();
	    Document doc = null;
	    String text = "";
	    
	    try {
	      doc = sax.build(fileName);
	      XPathExpression<Element> expr = xFactory.compile("//sentence",
	          Filters.element());
	      List<Element> sentences = expr.evaluate(doc);

	      /*
	      List<String> removeAspects = new ArrayList<>();
	      removeAspects.add("DRINKS#PRICES");
	      removeAspects.add("LOCATION#GENERAL");
	      removeAspects.add("DRINKS#STYLE_OPTIONS");
	      removeAspects.add("DRINKS#QUALITY");
	      */
	      
	      int cantBalanced = 0;
	      
	      for (Element sent : sentences) {
	        Element opinionsElement = sent.getChild("Opinions");
	        String sentStringTmp = sent.getChildText("text");
	        String sentString = "";
	        List<List<Token>> segmentedSentence = tokenizeSentence(sentStringTmp, language);
	        for (List<Token> sentence : segmentedSentence) {
	          for (Token token : sentence) {
	        	  sentString += token.getTokenValue() + " ";
	          }
	        }
	        
	        List<String> removeAspects = new ArrayList<>();
	        
	        if (opinionsElement != null) {
	          //iterating over every opinion in the opinions element
	          List<Element> opinionList = opinionsElement.getChildren();

	    	  for (Element opinion : opinionList) {
	              String categoryString = opinion.getAttributeValue("category");
	              removeAspects.add(categoryString);
	    	  }
	    	  
	    	  if (removeAspects.contains(aspect)) {
	    		  text += aspect + "\t" + sentString + "\n";
	    		  cantBalanced  ++;
	    	  } else {
	    		  if (cantBalanced > 0 ) {
	    			  text += "NO\t" + sentString + "\n";
	    			  cantBalanced--;
	    		  }
	    	  }
	    	  
	          
	        }
	      }//end of sentence
	    } catch (JDOMException | IOException e) {
	      e.printStackTrace();
	    }
	    
	    return text;
	  }
  
  public static String absa2015Toabsa2015AnotatedWithMultipleDocClasModels(String fileName, String modelsList) {
	    //reading the ABSA xml file
	    SAXBuilder sax = new SAXBuilder();
	    XPathFactory xFactory = XPathFactory.instance();
	    Document doc = null;
	    try {
	      doc = sax.build(fileName);
	      XPathExpression<Element> expr = xFactory.compile("//sentence",
	          Filters.element());
	      List<Element> sentences = expr.evaluate(doc);
	      
	      int cantSent = 0;

	      for (Element sent : sentences) {
	    	   
	        Element opinionsElement = sent.getChild("Opinions");
	        if (opinionsElement != null) {
	          //iterating over every opinion in the opinions element
	          List<Element> opinionList = opinionsElement.getChildren();
	          for (int i = opinionList.size()-1; i >= 0; i--) {
	        	  Element opinion = opinionList.get(i);
	        	  opinionsElement.removeContent(opinion);
	          }
	        }
	        
	        KAFDocument kaf;
			final String lang = "en";
		    final String kafVersion = "1.0";
			kaf = new KAFDocument(lang, kafVersion);
			final Properties properties = new Properties();
		    properties.setProperty("language", lang);
		    properties.setProperty("normalize", "default");
		    properties.setProperty("untokenizable", "no");
		    properties.setProperty("hardParagraph", "no");
		    InputStream inputStream = new ByteArrayInputStream(sent.getChildText("text").getBytes(Charset.forName("UTF-8")));
		    BufferedReader breader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
		    final eus.ixa.ixa.pipe.tok.Annotate annotator = new eus.ixa.ixa.pipe.tok.Annotate(breader, properties);
		    annotator.tokenizeToKAF(kaf);

		    //System.err.println(kaf.toString());
		    
		    BufferedReader reader = new BufferedReader(new FileReader(modelsList));
		    int lines = 0;
		    while (reader.readLine() != null) lines++;
		    reader.close();
		    
		    boolean Binary = false;
		    if (lines>1) Binary = true; 

	        File file = new File(modelsList);
			FileReader fileReader = new FileReader(file);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				//System.err.println("-" + line + "-" + kaf.getLang());
				
				/*
				File fileTmp = new File(line);
				String fileTmp0 = Paths.get(".").toAbsolutePath().normalize().toString()+"/tmpModels/"+line+"."+cantSent;
				File fileTmp2 = new File(fileTmp0);
				Files.copy(fileTmp.toPath(), fileTmp2.toPath());
				*/
				Properties oteProperties = new Properties();
			    oteProperties.setProperty("model", line);
			    oteProperties.setProperty("language", kaf.getLang());
			    oteProperties.setProperty("clearFeatures", "no");
			    
			    //eus.ixa.ixa.pipe.doc.Annotate docClassifier = new eus.ixa.ixa.pipe.doc.Annotate(oteProperties);
			    //docClassifier.classify(kaf);
			    
			    StatisticalDocumentClassifier docClassifier = new StatisticalDocumentClassifier(oteProperties);
			    String source = oteProperties.getProperty("model");
			    
			    List<List<WF>> sentences0 = kaf.getSentences();
			    List<String> tokens = new ArrayList<>();
			    for (List<WF> sentence : sentences0) {
			      for (WF wf : sentence) {
			        tokens.add(wf.getForm());
			      }
			    }
			    String[] document = tokens.toArray(new String[tokens.size()]);
			    String label = docClassifier.classify(document);
			    //Topic topic = kaf.newTopic(label);
			    double[] probs = docClassifier.classifyProb(document);
			    //topic.setConfidence((float) probs[0]);
			    //topic.setSource(Paths.get(source).getFileName().toString());
			    //topic.setMethod("ixa-pipe-doc");
			    
			    SortedMap<Double, String> map = new TreeMap<Double, String>(Collections.reverseOrder());
			    
			    
			    //System.err.println("RESULTADO: " + docClassifier.getClassifierME().getAllLabels(probs));
			    System.err.println("SENTENCE:" + sent.getChildText("text"));
			    Double sum =0.0;
			    for (int i = 0 ; i < probs.length; i++){
			    	//System.err.println("RESULTADO: " + docClassifier.getClassifierME().getLabel(i) + "\t\t" + probs[i]);
			    	sum += probs[i];
			    	
			    	map.put(probs[i], docClassifier.getClassifierME().getLabel(i));
			    	
			    	//System.err.println("\t\tPUT: " + probs[i] + " -- " + docClassifier.getClassifierME().getLabel(i));
			    	
			    	//Topic topic = kaf.newTopic(docClassifier.getClassifierME().getLabel(i));
			    	//topic.setConfidence((float) probs[i]);
				    //topic.setSource(Paths.get(source).getFileName().toString());
				    //topic.setMethod("ixa-pipe-doc");
			    }
			    sum = sum / probs.length;
			    System.err.println("MEDIA: " + sum);
			    
			    Set<Double> Keys = map.keySet();
			    
			    boolean first = true;
			    for (Double key : Keys) {
			    	System.err.println("\t\t" + key + "\t" + map.get(key));
			    	if (Binary) {
			    		if (key >= 0.40) {
			    			Topic topic = kaf.newTopic(map.get(key));
					    	topic.setConfidence((float) key.floatValue());
						    topic.setSource(Paths.get(source).getFileName().toString());
						    topic.setMethod("ixa-pipe-doc");
			    		}
			    		break;
			    	}
			    	else {
			    		if (first) {
				    		first=false;
				    		/*if (key > 0.65 || (key < 0.20 && key > 0.10)) {
				    			Topic topic = kaf.newTopic(map.get(key));
						    	topic.setConfidence((float) key.floatValue());
							    topic.setSource(Paths.get(source).getFileName().toString());
							    topic.setMethod("ixa-pipe-doc");
							    //break;
				    		}
				    		else */
				    			if (key < 0.10){
				    			break;
				    		}
				    		else {
				    			Topic topic = kaf.newTopic(map.get(key));
						    	topic.setConfidence((float) key.floatValue());
							    topic.setSource(Paths.get(source).getFileName().toString());
							    topic.setMethod("ixa-pipe-doc");
				    		}
				    	}
				    	else if (key > 0.25){
				    			Topic topic = kaf.newTopic(map.get(key));
						    	topic.setConfidence((float) key.floatValue());
							    topic.setSource(Paths.get(source).getFileName().toString());
							    topic.setMethod("ixa-pipe-doc");
				    	}
			    	}
			    	
			    }
			    
			    
			    //Files.delete(fileTmp2.toPath());
			}
			fileReader.close();

		    //System.err.println(kaf.toString());
		    cantSent++;
		    
		    System.err.println("IsBinary: " + Binary);
		    
		    List<Topic> topicList = kaf.getTopics();
		    for (Topic topic : topicList) {
		    	//System.err.println(topic.getTopicValue());
		    	if (!topic.getTopicValue().equals("NO")) {
		    		Element opinionElem = new Element("Opinion");
		    		opinionElem.setAttribute("target", "na");
		            opinionElem.setAttribute("category", topic.getTopicValue());
		            //TODO we still do not have polarity here
		            opinionElem.setAttribute("polarity", String.valueOf(topic.getConfidence()));
		            opinionElem.setAttribute("from", "0");
		            opinionElem.setAttribute("to", "0");
		            opinionsElement.addContent(opinionElem);
		    	}
		    }
	        
	      }//end of sentence
	    } catch (JDOMException | IOException e) {
	      e.printStackTrace();
	    }
	    
	    XMLOutputter xmlOutput = new XMLOutputter();
	    Format format = Format.getPrettyFormat();
	    xmlOutput.setFormat(format);
	    return xmlOutput.outputString(doc);
  }
  
  public static String absa2015Toabsa2015AnotatedWithMultipleDocClasModelsX(String fileName, String modelsList) {
	    //reading the ABSA xml file
	    SAXBuilder sax = new SAXBuilder();
	    XPathFactory xFactory = XPathFactory.instance();
	    Document doc = null;
	    try {
	      doc = sax.build(fileName);
	      XPathExpression<Element> expr = xFactory.compile("//sentence",
	          Filters.element());
	      List<Element> sentences = expr.evaluate(doc);
	      
	      //int cantSent = 0;

	      for (Element sent : sentences) {
	    	   
	        Element opinionsElement = sent.getChild("Opinions");
	        if (opinionsElement != null) {
	          //iterating over every opinion in the opinions element
	          List<Element> opinionList = opinionsElement.getChildren();
	          for (int i = opinionList.size()-1; i >= 0; i--) {
	        	  Element opinion = opinionList.get(i);
	        	  opinionsElement.removeContent(opinion);
	          }
	        }
	        
	        
	        Path pathx = FileSystems.getDefault().getPath("./", "TEXT.txt");
	        Files.deleteIfExists(pathx);
	        
	        File f = new File("TEXT.txt"); 
			FileUtils.writeStringToFile(f, sent.getChildText("text"), "UTF-8");
	        
	        /*Path path1 = FileSystems.getDefault().getPath("./", "NAF1.txt");
			Files.deleteIfExists(path1);
			String[] cmd1 = { "/bin/sh", "-c", "cat TEXT.txt | java -jar /home/vector/Documents/Ixa-git/ixa-pipe-tok/target/ixa-pipe-tok-1.8.5-exec.jar tok -l en > NAF1.txt" };
			Process proc1 = Runtime.getRuntime().exec(cmd1);
			
			try {
				if(!proc1.waitFor(30, TimeUnit.MINUTES)) {
				    //timeout - kill the process. 
				    proc1.destroy(); // consider using destroyForcibly instead
				    throw new Exception("TimeOut Expired in IXA-PIPE-TOK");
				}
			}catch (Exception e) {
				System.out.println("	ERROR: ");
			}*/

		    //System.err.println(kaf.toString());

	        File file = new File(modelsList);
			FileReader fileReader = new FileReader(file);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String line;
			String nextCommand = "";
			
			//int port = 2000;
			while ((line = bufferedReader.readLine()) != null) {
				//System.err.println("-" + line + "-" + kaf.getLang());
				System.err.println("	Model: " + line);
				
				//nextCommand +=" | java -jar /home/vector/Documents/Ixa-git/ixa-pipe-doc/target/ixa-pipe-doc-0.0.2-exec.jar client -p " + port;
				
				nextCommand +=" | java -jar /home/vector/Documents/Ixa-git/ixa-pipe-doc/target/ixa-pipe-doc-0.0.2-exec.jar tag -m " + line;
				
				
				//File fileTmp = new File("NAF.txt");
				//File fileTmp2 = new File("NAF1.txt");
				//Files.copy(fileTmp.toPath(), fileTmp2.toPath(), StandardCopyOption.REPLACE_EXISTING);
				//Files.delete(fileTmp.toPath());
				
				//port ++;
			}
			fileReader.close();
			
			String[] cmd = { "/bin/sh", "-c", "cat TEXT.txt | java -jar /home/vector/Documents/Ixa-git/ixa-pipe-tok/target/ixa-pipe-tok-1.8.5-exec.jar tok -l en" + nextCommand + " > NAF.txt" };
			
			//System.err.println("cat TEXT.txt | java -jar /home/vector/Documents/Ixa-git/ixa-pipe-tok/target/ixa-pipe-tok-1.8.5-exec.jar tok -l en" + nextCommand + " > NAF.txt");
			
			Process proc = Runtime.getRuntime().exec(cmd);
			
			try {
				if(!proc.waitFor(30, TimeUnit.MINUTES)) {
				    //timeout - kill the process. 
				    proc.destroy(); // consider using destroyForcibly instead
				    throw new Exception("TimeOut Expired in IXA");
				}
			}catch (Exception e) {
				System.out.println("	ERROR: ");
			}
			
		    //System.err.println(kaf.toString());
		    //cantSent++;
		    
			/*try {
	            Thread.sleep(1000);
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }*/
			
		    File fileDir = new File("NAF.txt");
		    
		    System.err.println("Terminado: " + sent.getChildText("text"));

			BufferedReader breader1 = new BufferedReader(new InputStreamReader(new FileInputStream(fileDir), "UTF-8"));

			KAFDocument kaf = null;
			
			try {kaf = KAFDocument.createFromStream(breader1);}
			catch (Exception e) {
				System.err.println("ENTRA A ERROR");
				e.printStackTrace();
				continue;
			}
			
		    
		    List<Topic> topicList = kaf.getTopics();
		    for (Topic topic : topicList) {
		    	//System.err.println(topic.getTopicValue());
		    	if (!topic.getTopicValue().equals("NO")) {
		    		Element opinionElem = new Element("Opinion");
		    		opinionElem.setAttribute("target", "na");
		            opinionElem.setAttribute("category", topic.getTopicValue());
		            //TODO we still do not have polarity here
		            opinionElem.setAttribute("polarity", "na");
		            opinionElem.setAttribute("from", "0");
		            opinionElem.setAttribute("to", "0");
		            opinionsElement.addContent(opinionElem);
		    	}
		    }
	        
	      }//end of sentence
	    } catch (JDOMException | IOException e) {
	      e.printStackTrace();
	    }
	    
	    XMLOutputter xmlOutput = new XMLOutputter();
	    Format format = Format.getPrettyFormat();
	    xmlOutput.setFormat(format);
	    return xmlOutput.outputString(doc);
}
  
  
}
