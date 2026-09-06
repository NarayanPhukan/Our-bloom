package com.ourbloom.app.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.ourbloom.app.R
import com.ourbloom.app.data.FirestoreRepository
import com.ourbloom.app.util.ErrorReporter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val repository = FirestoreRepository()
    private val RC_SIGN_IN = 9001

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            checkCoupleAndNavigate()
        }
    }

    private fun checkCoupleAndNavigate() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = repository.getCurrentUser()
                if (user?.coupleId.isNullOrEmpty()) {
                    findNavController().navigate(R.id.action_loginFragment_to_setupCoupleFragment)
                } else {
                    findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
                }
            } catch (e: Exception) {
                Log.e("LoginFragment", "Error checking couple status", e)
                findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            view.visibility = View.INVISIBLE
        }
        
        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        val btnLogin = view.findViewById<Button>(R.id.btn_login)
        val btnGoogleLogin = view.findViewById<Button>(R.id.btn_google_sign_in)
        val tvRegister = view.findViewById<TextView>(R.id.tv_register)
        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "Please enter both email and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Signing In..."

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    var signedIn = false
                    var serverCoupleId: String? = null

                    // 1. Direct Firebase Auth sign in
                    try {
                        auth.signInWithEmailAndPassword(email, password).await()
                        signedIn = true
                    } catch (fbEx: Exception) {
                        Log.d("LoginFragment", "Direct Firebase sign-in failed: ${fbEx.message}. Trying backend fallback...")
                    }

                    // 2. Server fallback (handles web-created MongoDB accounts and custom token sync)
                    if (!signedIn) {
                        val serverRes = repository.loginWithServer(email, password)
                        if (serverRes.success && !serverRes.firebaseCustomToken.isNullOrEmpty()) {
                            auth.signInWithCustomToken(serverRes.firebaseCustomToken).await()
                            signedIn = true
                            serverCoupleId = serverRes.coupleId
                            if (!serverCoupleId.isNullOrEmpty()) {
                                auth.currentUser?.uid?.let { uid ->
                                    repository.saveUserCoupleId(uid, serverCoupleId)
                                }
                            }
                        } else {
                            val errorMsg = serverRes.error ?: "Incorrect email or password."
                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                            ErrorReporter.notifyError("Sign In Failed", errorMsg, screenName = "LoginFragment")
                            btnLogin.isEnabled = true
                            btnLogin.text = "Sign In"
                            return@launch
                        }
                    }

                    // 3. User is authenticated, route depending on whether they have a garden
                    val currentUser = repository.getCurrentUser()
                    val resolvedCoupleId = currentUser?.coupleId ?: serverCoupleId
                    if (resolvedCoupleId.isNullOrEmpty()) {
                        findNavController().navigate(R.id.action_loginFragment_to_setupCoupleFragment)
                    } else {
                        findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
                    }
                } catch (e: Exception) {
                    Log.e("LoginFragment", "Sign in error", e)
                    val msg = e.message ?: "Authentication failed."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    ErrorReporter.notifyError("Sign In Error", msg, e, "LoginFragment")
                    btnLogin.isEnabled = true
                    btnLogin.text = "Sign In"
                }
            }
        }
        
        btnGoogleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        return view
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                val msg = "Google sign in failed: ${e.statusCode}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                ErrorReporter.notifyError("Google Sign-In Failed", msg, e, "LoginFragment")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    checkCoupleAndNavigate()
                } else {
                    val msg = task.exception?.message ?: "Authentication failed."
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    ErrorReporter.notifyError("Authentication Failed", msg, task.exception, "LoginFragment")
                }
            }
    }
}
