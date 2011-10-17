/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.opennlp.caseditor.sentdetect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.util.Span;

import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.opennlp.caseditor.namefinder.Entity;
import org.apache.opennlp.caseditor.util.UIMAUtil;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.caseditor.editor.AnnotationEditor;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Display;

public class SentenceContentProvider implements IStructuredContentProvider {

  private SentenceDetectorViewPage sentenceDetectorView;
  
  private AnnotationEditor editor;
  
  private ICasDocument document;
  
  private SentenceDetectorJob sentenceDetector;
  
  private TableViewer sentenceList;

  public SentenceContentProvider(SentenceDetectorViewPage sentenceDetectorView, AnnotationEditor editor,
      SentenceDetectorJob sentenceDetector, TableViewer sentenceList) {
    this.sentenceDetectorView = sentenceDetectorView;
    this.editor = editor;
    this.sentenceDetector = sentenceDetector;
    this.sentenceList = sentenceList;
    
    sentenceDetector.addJobChangeListener(new JobChangeAdapter() {
      public void done(final IJobChangeEvent event) {
        Display.getDefault().asyncExec(new Runnable() {
          
          @Override
          public void run() {
            if (event.getResult().isOK()) {
              
              SentenceContentProvider.this.sentenceDetectorView.setMessage(null);
              
              Entity sentences[] = SentenceContentProvider.this.
                  sentenceDetector.getDetectedSentences();
              
              SentenceContentProvider.this.sentenceList.refresh();
              SentenceContentProvider.this.sentenceList.add(sentences);
            }
            else {
              SentenceContentProvider.this.sentenceDetectorView.setMessage(event.getResult().getMessage());
            }
          }
        });
      }
    });
  }
  
  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    
    if (oldInput != null) {
      // Remove listeners ...
    }
    
    if (newInput != null) {
      document = (ICasDocument) newInput;
    }
  }
  
  void triggerSentenceDetector() {
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    
    CAS cas = document.getCAS();
    
    String paragraphTypeNames = store.getString(OpenNLPPreferenceConstants.PARAGRAPH_TYPE);
    Type paragraphTypes[] = UIMAUtil.splitTypes(paragraphTypeNames, ',', cas.getTypeSystem());
    
    List<Span> paragraphSpans = new ArrayList<Span>();
    
    if (paragraphTypes != null) {
      
      for (Iterator<AnnotationFS> sentenceIterator = UIMAUtil.createMultiTypeIterator(cas, paragraphTypes);
            sentenceIterator.hasNext();) {

        AnnotationFS paragraphAnnotation = sentenceIterator.next();
        
        paragraphSpans.add(
            new Span(paragraphAnnotation.getBegin(), paragraphAnnotation.getEnd()));
      }
    }
    else {
      if (paragraphTypeNames.trim().isEmpty()) {
        paragraphSpans.add(new Span(0, cas.getDocumentText().length()));
      }
      else {
        sentenceDetectorView.setMessage("A paragraph type cannot be found in the type system!");
        return;
      }
    }
    
    
    String modelPath = store.getString(OpenNLPPreferenceConstants.SENTENCE_DETECTOR_MODEL_PATH);
    
    sentenceDetector.setModelPath(modelPath);
    sentenceDetector.setParagraphs(paragraphSpans);
    sentenceDetector.setText(document.getCAS().getDocumentText());
    
    String sentenceTypeName = store.getString(OpenNLPPreferenceConstants.SENTENCE_TYPE);
    
    if (sentenceTypeName.isEmpty()) {
      sentenceDetectorView.setMessage("Sentence type name is not set!");
      return;
    }
      
    Type sentenceType = cas.getTypeSystem().getType(sentenceTypeName);
    
    if (sentenceType == null) {
      sentenceDetectorView.setMessage("Type system does not contain sentence type!");
      return;
    }
    
    sentenceDetector.setSentenceType(sentenceType.getName());
    
    sentenceDetector.schedule();
  }
  

  @Override
  public Object[] getElements(Object inputElement) {
    return new Object[0];
  }
  
  @Override
  public void dispose() {
  }
}
