# JNI: die nativen Symbole in libwhisperloom.so heissen Java_com_chris_whisperloom_whisper_WhisperLib_*
# -> Klassen- und Methodennamen duerfen von R8 nicht umbenannt werden.
-keep class com.chris.whisperloom.whisper.WhisperLib { *; }

# LiteRT-LM (lokales Textmodell): das AAR bringt keine Consumer-Regeln mit. Die JNI-Seite sucht
# Getter und Callbacks per Name; entfernt oder umbenennt R8 sie, endet nativeCreateConversation
# im Release mit SIGABRT (mid == null) — nicht abfangbar, im Debug-Build unsichtbar.
# https://github.com/google-ai-edge/LiteRT-LM/issues/3739
-keep class com.google.ai.edge.litertlm.** { *; }
