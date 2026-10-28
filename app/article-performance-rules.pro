# Instrumentation calls these entry points directly. Keep their contracts while
# allowing optimization of their bodies and the rest of the release application.
# This file is used only with articlePerformanceTest on the dedicated test ID.
-keep,allowoptimization class com.example.c001apk.compose.ui.feed.article.** { *; }
-keep,allowoptimization class com.example.c001apk.compose.ui.feed.reply.ReplyActivity { *; }
-keep,allowoptimization class com.example.c001apk.compose.util.CookieUtil { *; }
-keep,allowoptimization class com.example.c001apk.compose.ui.component.PublishMenuButtonKt { *; }
-keep,allowoptimization class com.example.c001apk.compose.ui.theme.ThemeKt { *; }
-keep,allowoptimization class androidx.tracing.Trace { *; }
-keep class androidx.compose.ui.platform.ViewRootForTest { *; }
-keep class androidx.compose.ui.platform.ViewRootForTest$Companion { *; }
-keep,allowoptimization class androidx.compose.ui.node.RootForTest { *; }
-keep,allowoptimization class androidx.compose.ui.semantics.SemanticsNode { *; }
-keep,allowoptimization class androidx.compose.ui.semantics.SemanticsOwner { *; }
-keep,allowoptimization class androidx.compose.ui.semantics.SemanticsConfiguration { *; }
-keep,allowoptimization class com.google.gson.Gson { public *; }
# Test libraries link against the target's Kotlin facade/enum contracts. R8
# otherwise removes unused target APIs such as LazyKt before the test is linked.
-keep,allowoptimization class kotlin.** { *; }
-keep,allowoptimization class androidx.compose.ui.semantics.** { *; }
-keep,allowoptimization class androidx.lifecycle.ViewModelProvider { *; }
-keep,allowoptimization class androidx.lifecycle.ViewModelProvider$Companion { *; }
-keep,allowoptimization class androidx.compose.ui.text.AnnotatedString { *; }
