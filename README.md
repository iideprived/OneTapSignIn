# OneTapSignIn
Easily implement the Google One Tap Sign-In Dialog into your Jetpack Compose app.

## One Tap Sign In
Learn more about Google's One Tap Sign In here:
https://developers.google.com/identity/one-tap/android/overview
![image](https://github.com/iideprived/OneTapSignIn/assets/117201446/7431799c-f084-41da-b859-6d2bdf63f276)

## Installation
To use this One Tap Sign In component, add Jitpack as a repository in your `project/build.gradle` or `settings.gradle` file
```groovy
// In project/build.gradle or settings.gradle
  repositories {
    ...
    maven("https://jitpack.io")
  }
```
Then, in your `module/build.gradle`, import the dependency
```groovy
  // In module/build.gradle
  dependencies {
    ...
    implementation("com.github.iideprived:OneTapSignIn:1.0.1")
  }
```

## Implementation
The implementation for the One Tap Sign In, using this component, is extremely simple. Simply store `val oneTapState = rememberOneTapSignIn(...)` and open the menu when the user needs to sign in.
```kotlin
@Composable
fun LoginPage() {
    val oneTapState = rememberOneTapSignIn("YOUR_CLIENT_ID") { credential ->
        Log.d("OneTapSignIn", "Success!")
        // TODO: Authenticate using something like Firebase
    }
  
    Button(onClick = { oneTapState.openMenu() } { Text("Sign In With Google") }
}
