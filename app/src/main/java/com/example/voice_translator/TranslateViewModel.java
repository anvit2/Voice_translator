package com.example.voice_translator;

import android.app.Application;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.util.LruCache;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class TranslateViewModel extends AndroidViewModel {
    private static  final int NUM_TRANSLATOR=3;


    private final RemoteModelManager modelManager;
    private final LruCache<TranslatorOptions, Translator> translators=
            new LruCache<TranslatorOptions, Translator>(NUM_TRANSLATOR){

                @Override
                protected Translator create(TranslatorOptions key) {
                    return Translation.getClient(key);
                }

                @Override
                protected void entryRemoved(boolean evicted, TranslatorOptions key, Translator oldValue, Translator newValue) {
                    oldValue.close();
                }
            };

    MutableLiveData<TranslateLanguage.Language> sourceLang= new MutableLiveData<>();
    MutableLiveData<TranslateLanguage.Language> targetLang= new MutableLiveData<>();
    MutableLiveData<String> sourceText= new MutableLiveData<>();
    MediatorLiveData<ResultOrError> translatedText= new MediatorLiveData<>();
    MediatorLiveData<List<String>> availableModels= new MediatorLiveData<>();
    public TranslateViewModel(@NonNull Application application) {
        super(application);
        modelManager = RemoteModelManager.getInstance();


        final OnCompleteListener<String> processTranslation = new OnCompleteListener<String>() {

            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (task.isSuccessful()) {
                    translatedText.setValue(new ResultOrError(task.getResult(), null));
                } else {
                    translatedText.setValue(new ResultOrError(null, task.getException()));
                }


            fetchDownloadedModels(); }
        };

       translatedText.addSource(sourceText, new Observer<TranslateLanguage.Language>() {
              @Override
            public void onChanged(TranslateLanguage.Language language) {

            }

            @Override
            public void onChanged(String S) {
                translate().addOnCompleteListener(processTranslation);
            }
        });

        Observer<Language> languageObserver = language -> translate().addOnCompleteListener(processTranslation);
        translatedText.addSource(sourceLang,languageObserver);
        translatedText.addSource(targetLang,languageObserver);

        fetchDownloadedModels();

    }
          List<Language> getAvailableLanguages() {
              List<Language> languages = new ArrayList<>();
              List<String> languageIds = TranslateLanguage.getAllLanguages();
              for (String languageId : languageIds) {
                  languages.add(
                          new Language(TranslateLanguage.fromLanguageTag(languageId))
                  );
              }
              return languages;
          }

          private  TranslateRemoteModel getModel(String languageCode){
        return  new TranslateRemoteModel.Builder(languageCode).build();
          }

          void downloadLanguage(Language language){
        TranslateRemoteModel model =getModel(TranslateLanguage.fromLanguageTag(language.getCode()));
        modelManager.download(model, new DownloadConditions.Builder().build()).addOnCompleteListener(new OnCompleteListener<Void>(){
                    @Override
                    public  void onComplete(@NonNull Task<Void> task){

                        fetchDownloadedModels();
                    }
                });
          }

          void deleteLanguage(Language language){
        TranslateRemoteModel model=
                getModel(TranslateLanguage.fromLanguageTag(language.getCode()));
        modelManager.deleteDownloadedModel(model).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                fetchDownloadedModels();
            }
        });
    }




    public Task<String> translate(){
        final String text= sourceText.getValue();
        final Language source= (Language) sourceLang.getValue();
        final Language target= (Language) targetLang.getValue();
        if(source== null || target== null || text== null || text.isEmpty()){
            return  Tasks.forResult("");
        }
        String sourceLangCode
                =TranslateLanguage.fromLanguageTag(source.getCode());
        String tragetLangCode
                =TranslateLanguage.fromLanguageTag(target.getCode());
        TranslatorOptions options= new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangCode)
                .setTargetLanguage(tragetLangCode)
                .build();
        return translators.get(options).downloadModelIfNeeded().continueWithTask(new Continuation<Void, Task<String>>() {
            @Override
            public Task<String> then(@NonNull Task<Void> task) throws Exception {
                if(task.isSuccessful()){
                    return translators.get(options).translate(text);
                }
                else{
                    Exception e= task.getException();
                    if(e== null){
                        e= new Exception(getApplication().getString(R.string.common_google_play_services_unknown_issue));
                    }
                    return Tasks.forException(e);
                }
            }
        });
    }

    private  void fetchDownloadedModels(){
        modelManager.getDownloadedModels(TranslateRemoteModel.class).addOnSuccessListener(new OnSuccessListener<Set<TranslateRemoteModel>>() {
            @Override
            public void onSuccess(Set<TranslateRemoteModel> translateRemoteModels) {
                List<String> modelCodes= new ArrayList<>(translateRemoteModels.size());
                for(TranslateRemoteModel model: translateRemoteModels){
                    modelCodes.add(model.getLanguage());
                }
                Collections.sort(modelCodes);
                availableModels.setValue(modelCodes);
            }
        });
    }



    static class ResultOrError{
        final @Nullable String result;
        final @Nullable Exception error;

        ResultOrError(@Nullable String result, @Nullable Exception error){
            this.result=result;
            this.error=error;
        }
    }
     static class Language implements  Comparable<Language>{
        private String code;
        Language(String code){
            this.code=code;
        }
        String getDisplayName(){
            return new Locale(code).getDisplayName();
        }
        String getCode(){
            return code;
        }
        public boolean equals(Object o){
            if(o== this){
                return true;
            }
            if(!(o instanceof Language)){
                return  false;
            }
            Language otherLang=(Language) o;
            return  otherLang.code.equals(code);
        }

           @NonNull
      public String toString(){
            return code+ " - "+getDisplayName();
           }

      public int hashCode() {
          return code.hashCode();
      }
      @Override
      public int compareTo(@NonNull Language o){
            return  this.getDisplayName().compareTo(o.getDisplayName());
      }

  }
    @Override
    protected void onCleared(){
        super.onCleared();
        translators.evictAll();
    }
}
