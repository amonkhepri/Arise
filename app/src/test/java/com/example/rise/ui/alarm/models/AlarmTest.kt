package com.example.rise.ui.alarm.models

import org.junit.Assert.assertFalse
import org.junit.Test

class AlarmTest {

  @Test
  fun `alarm model does not carry firestore annotations`() {
    val firestoreAnnotationName = "com.google.firebase.firestore.IgnoreExtraProperties"
    val annotationNames = Alarm::class.java.annotations
      .map { it.annotationClass.qualifiedName.orEmpty() }

    assertFalse(annotationNames.contains(firestoreAnnotationName))
  }
}
