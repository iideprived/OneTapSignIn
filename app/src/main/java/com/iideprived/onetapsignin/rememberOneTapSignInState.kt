package com.iideprived.onetapsignin

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.BeginSignInRequest.PasskeyJsonRequestOptions
import com.google.android.gms.auth.api.identity.BeginSignInRequest.PasskeysRequestOptions
import com.google.android.gms.auth.api.identity.BeginSignInRequest.PasswordRequestOptions
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInCredential
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException


/**
 * See https://developers.google.com/identity/one-tap/android/overview
 * Implement tne Google One Tap Sign-In. Request to open the Google One Tap Sign-In dialog by calling {@code state.openMenu()}
 * Once the dialog is open, they will hopefully be guided through logging in or creating an account with Google.
 * Credentials and errors during the process are captured.
 *
 * This does not render any graphics onto the screen besides the Google One Tap Sign-In Dialog
 *
 * @author iideprived
 * @author Herbert Smith
 * @param webClientId - Obtained either through the Firebase Console, or Google Cloud Console. See https://developers.google.com/identity/one-tap/android/get-started
 * @param signInRequestOptions - A series of extra parameters to feed the One Tap Client. Null values will use the default behavior of the One Tap Client for that parameter
 * @param tokenOptions - A series of extra parameters for the {@code GoogleIdRequestTokenOptions} for the One Tap Client. Null values will use the default behavior of the One Tap Client for that parameter.
 * @param onError - When the One Tap Sign In Dialog fails to open, and when credentials are not returned
 * @param onCredentialReceived - When credentials are received from the One Tap Sign In Dialog. Use these credentials to sign in with Firebase, or another server.
 *
 * @return OneTapSignInState - A state controller for the One Tap Client. Call ${@code state.openMenu()} when the Sign In With Google button is clicked
 * @sample {@code
 *     val oneTapSignInState = rememberOneTapSignInState(
 *         webClientId = getString(R.strings.web_client_id),
 *         onError = { Log.d("OneTapSignIn", "There was an error: ${it.message}") },
 *     ) { credential ->
 *          Toast.makeText(LocalContext.current, "Welcome back, ${credential.givenName}!", Toast.LENGTH_LONG).show()
 *          val googleCredentials = GoogleAuthProvider.getCredential(credential.googleIdToken, null)
 *          val auth = FirebaseAuth.getInstance()
 *          auth.signInWithCredential(googleCredential)
 *              .addOnSuccessListener { Log.d("OneTapSignIn", "Sign In Complete") /* Navigate to Home Screen */ }
 *              .addOnFailureListener { Log.d("OneTapSignIn", "User not signed in") /* Save and retry */ }
 *     }
 *
 *     Button(onClick = { oneTapSignInState.openMenu() }) { Text("Sign In With Google") }
 * }
 *
 * @since September 15, 2023
 */
@Composable
fun rememberOneTapSignInState(
    webClientId: String,
    signInRequestOptions: SignInRequestOptions = SignInRequestOptions(),
    tokenOptions: GoogleIdRequestTokenOptions = GoogleIdRequestTokenOptions(),
    onError: (Exception) -> Unit = {},
    onCredentialReceived: (SignInCredential) -> Unit,

    ) : OneTapSignInState {
    val state = remember { OneTapSignInState() }

    val oneTapClient = Identity.getSignInClient(LocalContext.current)
    val runner = LocalLifecycleOwner.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@rememberLauncherForActivityResult
            }
            runner.lifecycleScope.launch {
                try {
                    onCredentialReceived(oneTapClient.getSignInCredentialFromIntent(result.data))
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }
    )

    LaunchedEffect(state.isOpened){
        if (!state.isOpened) return@LaunchedEffect

        val result = try{
            oneTapClient.beginSignIn(
                BeginSignInRequest.Builder()
                    .apply {
                        signInRequestOptions.passkeyJsonSignInRequestOptions?.let { setPasskeyJsonSignInRequestOptions(it) }
                        signInRequestOptions.passwordRequestOptions?.let { setPasswordRequestOptions(it) }
                        signInRequestOptions.passkeysSignInRequestOptions?.let { setPasskeysSignInRequestOptions(it) }
                        signInRequestOptions.autoSelect?.let { setAutoSelectEnabled(it) }
                    }
                    .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.Builder()
                            .apply {
                                setServerClientId(webClientId)
                                tokenOptions.supported?.let { setSupported(it) }
                                tokenOptions.filterByAuthorizedAccounts?.let { setFilterByAuthorizedAccounts(it) }
                                tokenOptions.requestVerifiedPhoneNumber?.let { setRequestVerifiedPhoneNumber(it) }
                                tokenOptions.nonce?.let { setNonce(it) }
                            }
                            .build()
                    )
                    .build()
            ).await()
            // Returns result as successful
        } catch (e: Exception) {
            try {
                oneTapClient.beginSignIn(
                    BeginSignInRequest.Builder()
                        .apply {
                            signInRequestOptions.passkeyJsonSignInRequestOptions?.let { setPasskeyJsonSignInRequestOptions(it) }
                            signInRequestOptions.passwordRequestOptions?.let { setPasswordRequestOptions(it) }
                            signInRequestOptions.passkeysSignInRequestOptions?.let { setPasskeysSignInRequestOptions(it) }
                            setAutoSelectEnabled(false)
                        }
                        .setGoogleIdTokenRequestOptions(
                            BeginSignInRequest.GoogleIdTokenRequestOptions.Builder()
                                .apply {
                                    setServerClientId(webClientId)
                                    setFilterByAuthorizedAccounts(false)
                                    setSupported(true)
                                    tokenOptions.requestVerifiedPhoneNumber?.let { setRequestVerifiedPhoneNumber(it) }
                                    tokenOptions.nonce?.let { setNonce(it) }
                                }
                                .build()
                        )
                        .build()
                ).await()
                // Retries without filtering authorized accounts and auto-selecting
            } catch (e: Exception){
                onError(e)
                state.closeMenu()
                if ( e is CancellationException) throw e
                null
                // Returns null if there is a problem
            }
        }
        val signIn = result?.pendingIntent?.intentSender ?: return@LaunchedEffect

        launcher.launch(IntentSenderRequest.Builder(signIn).build())
    }

    return state
}

class OneTapSignInState{
    var isOpened: Boolean by mutableStateOf(false)
        private set

    fun openMenu() { isOpened = true }
    internal fun closeMenu() { isOpened = false }
}

data class SignInRequestOptions(
    val passkeysSignInRequestOptions: PasskeysRequestOptions? = null,
    val passwordRequestOptions: PasswordRequestOptions? = null,
    val passkeyJsonSignInRequestOptions: PasskeyJsonRequestOptions? = null,
    val autoSelect: Boolean? = false,
)

data class GoogleIdRequestTokenOptions(
    val supported: Boolean? = true,
    val filterByAuthorizedAccounts: Boolean? = false,
    val requestVerifiedPhoneNumber: Boolean? =  null,
    val nonce: String? = null,
)