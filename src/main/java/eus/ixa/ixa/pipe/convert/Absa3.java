package eus.ixa.ixa.pipe.convert;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import eus.ixa.ixa.pipe.ml.StatisticalDocumentClassifier;
import eus.ixa.ixa.pipe.ml.tok.RuleBasedTokenizer;
import eus.ixa.ixa.pipe.ml.tok.Token;
import eus.ixa.ixa.pipe.opinion.AnnotateTargets;
import eus.ixa.ixa.pipe.opinion.DocAnnotateAspects;
import ixa.kaflib.KAFDocument;
import ixa.kaflib.Opinion;
import ixa.kaflib.Opinion.OpinionExpression;
import ixa.kaflib.Term;
import ixa.kaflib.WF;

public class Absa3 {
	
	private Absa3() {}
	

	  public static String absa2015ToNAFAnotatedWith3ModelsX(final InputStream inputStream, String SequenceModel, String MultiDocCatModel, String BinaryDocCatModelList) {
		  //SAXBuilder sax = new SAXBuilder();
		  //XPathFactory xFactory = XPathFactory.instance();
		  //Document doc = null;

		  //System.err.println("EntraX");
		  KAFDocument kaf = null;
		  KAFDocument kafTmp = null;
		  
		  try {
			  /*doc = sax.build(fileName);
		      XPathExpression<Element> expr = xFactory.compile("//sentence",
		          Filters.element());
		      List<Element> sentences = expr.evaluate(doc);
		      
		      int cantSent = 0;
		      
		      String Document = "";

		      for (Element sent : sentences) {
		    	  Document += sent.getChildText("text") + "\n";
		      }
		      

	    	  final String lang = language;
			  final String kafVersion = "1.0";
			  kaf = new KAFDocument(lang, kafVersion);
			  final Properties properties = new Properties();
			  properties.setProperty("language", lang);
			  properties.setProperty("normalize", "default");
			  properties.setProperty("untokenizable", "no");
			  properties.setProperty("hardParagraph", "no");
			  InputStream inputStream = new ByteArrayInputStream(Document.getBytes(Charset.forName("UTF-8")));
			  BufferedReader breader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
			  final eus.ixa.ixa.pipe.tok.Annotate annotator = new eus.ixa.ixa.pipe.tok.Annotate(breader, properties);
			  annotator.tokenizeToKAF(kaf);
			  
			  final Properties propertiesPos = new Properties();
			  propertiesPos.setProperty("model", "/home/vector/Documents/Ixa-git/ixa-pipe-pos/morph-models-1.5.0/en/en-pos-perceptron-autodict01-conll09.bin");
			  propertiesPos.setProperty("lemmatizerModel", "/home/vector/Documents/Ixa-git/ixa-pipe-pos/morph-models-1.5.0/en/en-lemma-perceptron-conll09.bin");
			  propertiesPos.setProperty("language", lang);
			  propertiesPos.setProperty("multiwords", "");
			  propertiesPos.setProperty("dictag", "");

			  final eus.ixa.ixa.pipe.pos.Annotate posAnnotator = new eus.ixa.ixa.pipe.pos.Annotate(propertiesPos);
			  posAnnotator.annotatePOSToKAF(kaf);
			  */
			  
			  BufferedReader breader = new BufferedReader(new InputStreamReader(
				        inputStream, "UTF-8"));
			  kaf = KAFDocument.createFromStream(breader);
			  final String lang = kaf.getLang();
			  
			  InputStream inputStream1 = new ByteArrayInputStream(kaf.toString().getBytes(Charset.forName("UTF-8")));
			  BufferedReader breader1 = new BufferedReader(new InputStreamReader(inputStream1, "UTF-8"));
			  kafTmp = KAFDocument.createFromStream(breader1);
			  
			  final Properties propertiesOte = new Properties();

			  propertiesOte.setProperty("model", SequenceModel);
			  propertiesOte.setProperty("language", lang);
			  propertiesOte.setProperty("clearFeatures", "no");
			  final AnnotateTargets oteAnnotator = new AnnotateTargets(propertiesOte);
			  oteAnnotator.annotate(kaf);
			  oteAnnotator.annotateToNAF(kaf);
			  
			  File file = new File(BinaryDocCatModelList);
			  FileReader fileReader = new FileReader(file);
			  BufferedReader bufferedReader = new BufferedReader(fileReader);
			  String line;
			  List<String> MultipleModels = new ArrayList<>();
			  MultipleModels.add(MultiDocCatModel);
			  while ((line = bufferedReader.readLine()) != null) {
				  //MultipleModels.add(line);
			  }
			  fileReader.close();
			  
			  for (String model : MultipleModels ) {
				  final Properties aspectProperties = new Properties();

				  aspectProperties.setProperty("tagger", "doc");
				  aspectProperties.setProperty("model", model);
				  aspectProperties.setProperty("language", lang);
				  aspectProperties.setProperty("clearFeatures", "no");
				  final DocAnnotateAspects aspectExtractor = new DocAnnotateAspects(aspectProperties);
				  aspectExtractor.annotate(kafTmp);
				  aspectExtractor.annotateToNAF(kafTmp);
				  
				  
				  List<Opinion> OpinionsFromTmp = kafTmp.getOpinions();
				  for (Opinion OpinionFromTmp : OpinionsFromTmp) {
					  OpinionExpression OpinionExp = OpinionFromTmp.getOpinionExpression();		
					  List<Term> Terms = OpinionExp.getTerms();
					  String Sentiment = OpinionExp.getSentimentProductFeature();
					  Boolean Exist = false;
					  outerloop:
					  for (Term term : Terms) {
						
						  List<Opinion> OpinionsFromKaf = kaf.getOpinions();
						  for (Opinion OpinionFromkaf : OpinionsFromKaf) {
							  OpinionExpression OpinionExpkaf = OpinionFromkaf.getOpinionExpression();		
							  List<Term> Termskaf = OpinionExpkaf.getTerms();
							  String Sentimentkaf = OpinionExpkaf.getSentimentProductFeature();
							  for (Term termkaf : Termskaf) {
								  if (termkaf.getId().equalsIgnoreCase(term.getId()) && Sentiment.equalsIgnoreCase(Sentimentkaf)) {
									  Exist = true;
									  break outerloop;
								  }
							  }
						  }
					  }
					  if (!Exist && !Sentiment.equalsIgnoreCase("NO")) {
						  Opinion opinion = kaf.newOpinion();
						  ixa.kaflib.Span<Term> aspectSpan = KAFDocument.newTermSpan(Terms);
						  OpinionExpression opExpression = opinion.createOpinionExpression(aspectSpan);
					      opExpression.setSentimentProductFeature(Sentiment);
					  }
					  
				  }
			  }
			  
			  
			  
			  
		      
		  } catch (Exception e) {
			// TODO: handle exception
			  e.printStackTrace();
			  return "";
		  }
		  return kaf.toString();
	  }
	  
	  public static String absa2015ToNAFAnotatedWith3Models(final InputStream inputStream, String SequenceModel, String MultiDocCatModel, String BinaryDocCatModelList) {

		  KAFDocument kaf = null;
		  KAFDocument kafTmp = null;
		  
		  try {
			  
			  BufferedReader breader = new BufferedReader(new InputStreamReader(
				        inputStream, "UTF-8"));
			  kaf = KAFDocument.createFromStream(breader);
			  final String lang = kaf.getLang();
			  
			  InputStream inputStream1 = new ByteArrayInputStream(kaf.toString().getBytes(Charset.forName("UTF-8")));
			  BufferedReader breader1 = new BufferedReader(new InputStreamReader(inputStream1, "UTF-8"));
			  kafTmp = KAFDocument.createFromStream(breader1);
			  
			  final Properties propertiesOte = new Properties();

			  propertiesOte.setProperty("model", SequenceModel);
			  propertiesOte.setProperty("language", lang);
			  propertiesOte.setProperty("clearFeatures", "no");
			  final AnnotateTargets oteAnnotator = new AnnotateTargets(propertiesOte);
			  oteAnnotator.annotate(kaf);
			  oteAnnotator.annotateToNAF(kaf);
			  
			  File file = new File(BinaryDocCatModelList);
			  FileReader fileReader = new FileReader(file);
			  BufferedReader bufferedReader = new BufferedReader(fileReader);
			  String line;
			  List<String> MultipleModels = new ArrayList<>();
			  MultipleModels.add(MultiDocCatModel);
			  while ((line = bufferedReader.readLine()) != null) {
				  //MultipleModels.add(line);
			  }
			  fileReader.close();
			  
			  boolean Binary = false;
			  if (MultipleModels.size()>1) Binary = true; 
			  
			  for (String model : MultipleModels ) {
				  
				  /*
				  final Properties aspectProperties = new Properties();

				  aspectProperties.setProperty("tagger", "doc");
				  aspectProperties.setProperty("model", model);
				  aspectProperties.setProperty("language", lang);
				  aspectProperties.setProperty("clearFeatures", "no");
				  final DocAnnotateAspects aspectExtractor = new DocAnnotateAspects(aspectProperties);
				  aspectExtractor.annotate(kafTmp);
				  aspectExtractor.annotateToNAF(kafTmp);
				  */
				  
				  	Properties oteProperties = new Properties();
				    oteProperties.setProperty("model", model);
				    oteProperties.setProperty("language", kafTmp.getLang());
				    oteProperties.setProperty("clearFeatures", "no");
				    
				    StatisticalDocumentClassifier docClassifier = new StatisticalDocumentClassifier(oteProperties);
				    
				    List<List<WF>> sentences0 = kafTmp.getSentences();
				    for (List<WF> sentence : sentences0) {
				    	 String[] tokens = new String[sentence.size()];
				         String[] tokenIds = new String[sentence.size()];
				         for (int i = 0; i < sentence.size(); i++) {
				           tokens[i] = sentence.get(i).getForm();
				           tokenIds[i] = sentence.get(i).getId();
				         }
				         
				         List<Term> aspectTerms = kafTmp.getTermsFromWFs(Arrays.asList(Arrays
				                 .copyOfRange(tokenIds, 0, tokens.length)));
				         ixa.kaflib.Span<Term> aspectSpan = KAFDocument.newTermSpan(aspectTerms);

				         double[] probs = docClassifier.classifyProb(tokens);
				         
				         SortedMap<Double, String> map = new TreeMap<Double, String>(Collections.reverseOrder());
				         
				         Double sum =0.0;
				         for (int i = 0 ; i < probs.length; i++){
				        	 sum += probs[i];
				        	 map.put(probs[i], docClassifier.getClassifierME().getLabel(i));
				         }
				         sum = sum / probs.length;
				         Set<Double> Keys = map.keySet();
				         boolean first = false;
					    for (Double key : Keys) {
					    	System.err.println("\t\t" + key + "\t" + map.get(key));
					    	if (Binary) {
					    		if (key >= 0.35) {
					    			Opinion opinion = kafTmp.newOpinion();
					    		      //TODO expression span, perhaps heuristic around ote?
					    		      OpinionExpression opExpression = opinion.createOpinionExpression(aspectSpan);
					    		      opExpression.setSentimentProductFeature(map.get(key));
					    		}
					    		break;
					    	}
					    	else {
					    		if (first) {
						    		first=false;
						    		if (key < 0.10){
						    			break;
						    		}
						    		else {
						    			Opinion opinion = kafTmp.newOpinion();
						    		      //TODO expression span, perhaps heuristic around ote?
						    		      OpinionExpression opExpression = opinion.createOpinionExpression(aspectSpan);
						    		      opExpression.setSentimentProductFeature(map.get(key));
						    		}
						    	}
						    	else if (key > 0.40){
					    			Opinion opinion = kafTmp.newOpinion();
					    		      //TODO expression span, perhaps heuristic around ote?
					    		      OpinionExpression opExpression = opinion.createOpinionExpression(aspectSpan);
					    		      opExpression.setSentimentProductFeature(map.get(key));
						    	}
					    	}
					    	
					    }
				    }
				  
				  
				  
				  List<Opinion> OpinionsFromTmp = kafTmp.getOpinions();
				  for (Opinion OpinionFromTmp : OpinionsFromTmp) {
					  OpinionExpression OpinionExp = OpinionFromTmp.getOpinionExpression();		
					  List<Term> Terms = OpinionExp.getTerms();
					  String Sentiment = OpinionExp.getSentimentProductFeature();
					  Boolean Exist = false;
					  outerloop:
					  for (Term term : Terms) {
						
						  List<Opinion> OpinionsFromKaf = kaf.getOpinions();
						  for (Opinion OpinionFromkaf : OpinionsFromKaf) {
							  OpinionExpression OpinionExpkaf = OpinionFromkaf.getOpinionExpression();		
							  List<Term> Termskaf = OpinionExpkaf.getTerms();
							  String Sentimentkaf = OpinionExpkaf.getSentimentProductFeature();
							  for (Term termkaf : Termskaf) {
								  if (termkaf.getId().equalsIgnoreCase(term.getId()) && Sentiment.equalsIgnoreCase(Sentimentkaf)) {
									  Exist = true;
									  break outerloop;
								  }
							  }
						  }
					  }
					  if (!Exist && !Sentiment.equalsIgnoreCase("NO")) {
						  Opinion opinion = kaf.newOpinion();
						  ixa.kaflib.Span<Term> aspectSpan = KAFDocument.newTermSpan(Terms);
						  OpinionExpression opExpression = opinion.createOpinionExpression(aspectSpan);
					      opExpression.setSentimentProductFeature(Sentiment);
					  }
					  
				  }
			  }
			  
			  
			  
			  
		      
		  } catch (Exception e) {
			// TODO: handle exception
			  e.printStackTrace();
			  return "";
		  }
		  return kaf.toString();
	  }
	  
	  public static String nafToNafWithOpinions(String inputNAF, String AbsaXML, String language) throws IOException {

		    Path kafPath = Paths.get(inputNAF);
		    KAFDocument kaf = KAFDocument.createFromFile(kafPath.toFile());
		    
		    SAXBuilder sax = new SAXBuilder();
		    XPathFactory xFactory = XPathFactory.instance();
		    Document doc = null;
		    try {
		    	doc = sax.build(AbsaXML);
			    XPathExpression<Element> expr = xFactory.compile("//sentence",
			          Filters.element());
			    List<Element> sentences = expr.evaluate(doc);
			    
			    for (Element sentXML : sentences) {
			        Element opinionsElement = sentXML.getChild("Opinions");
			        //String sentStringTmp = sent.getChildText("text");
			        String SentIdString = sentXML.getAttributeValue("id");
			        
			        List<WF> sent = null;
			        for (List<WF> sentTmp : kaf.getSentences()) {
					      String reviewId = sentTmp.get(0).getXpath();
					      if (reviewId.equalsIgnoreCase(SentIdString)){
					    	  sent = sentTmp;
					      }
			        }
			        
			        String[] tokens = new String[sent.size()];
			        String[] tokenIds = new String[sent.size()];
			        for (int i = 0; i < sent.size(); i++) {
			          tokens[i] = sent.get(i).getForm();
			          tokenIds[i] = sent.get(i).getId();
			        }
			        
			        
			        if (opinionsElement != null) {
			          //iterating over every opinion in the opinions element
			          List<Element> opinionList = opinionsElement.getChildren();
			          
			          

			    	  for (Element opinion : opinionList) {
			    		  String targetString = opinion.getAttributeValue("target");
			              String categoryString = opinion.getAttributeValue("category");
			    		  List<List<Token>> segmentedSentence = tokenizeSentence(targetString, language);
					      List<Token> target = segmentedSentence.get(0);
					      String targetMin = target.get(0).getTokenValue();
		            	  String targetMax = target.get(target.size()-1).getTokenValue();
					      int posTargetMin=-1;
		            	  int posTargetMax=-1;
		            	  
		            	  if (targetString.equalsIgnoreCase("NULL")) {
		            		  posTargetMin=0;
		            		  posTargetMax=sent.size();
		            	  }
		            	  else {
		            		  int count = 0;
			            	  for (WF token : sent) {
			                	  if (token.getForm().equals(targetMin)) {
			                		  posTargetMin=count;
			                	  }
			                	  count++;
			                	  if (token.getForm().equals(targetMax) && posTargetMin > -1) {
			                		  posTargetMax=count;
			                		  break;
			                	  }

			                  }
		            	  }
					      
		            	  System.err.println(posTargetMin + " - " + posTargetMax);
		            	  List<Term> nameTerms = kaf.getTermsFromWFs(Arrays.asList(Arrays
		            	            .copyOfRange(tokenIds, posTargetMin, posTargetMax)));
		            	  ixa.kaflib.Span<Term> oteSpan = KAFDocument.newTermSpan(nameTerms);
		                  Opinion newOpinion = kaf.newOpinion();
		                  newOpinion.createOpinionTarget(oteSpan);
		                  //TODO expression span, perhaps heuristic around ote?
		                  OpinionExpression opExpression = newOpinion.createOpinionExpression(oteSpan);
		                  opExpression.setSentimentProductFeature(categoryString);
					      
					      
			    	  }
			    		  
			        }
			    }
		    }catch (JDOMException | IOException e) {
		      e.printStackTrace();
		    }
		    
		    	  
		    return kaf.toString();

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
	  
}
