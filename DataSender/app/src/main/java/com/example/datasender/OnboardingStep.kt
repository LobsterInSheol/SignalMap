package com.example.datasender

/**
 * Kroki onboardingu uprawnień aplikacji.
 * Określa, jakie zgody musi jeszcze nadać użytkownik,
 * zanim aplikacja przejdzie do normalnego trybu działania.
 */
enum class OnboardingStep {
    LOCATION,    // brak lokalizacji
    BACKGROUND,  // brak lokalizacji w tle
    PHONE,       // brak READ_PHONE_STATE
    DONE         // wszystkie zgody przyznane
}
