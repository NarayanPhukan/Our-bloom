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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val repository = FirestoreRepository()
    private val RC_SIGN_IN = 9002

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register, container, false)
        auth = FirebaseAuth.getInstance()
        
        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        val nameInput = view.findViewById<EditText>(R.id.et_name)
        val emailInput = view.findViewById<EditText>(R.id.et_email)
        val passwordInput = view.findViewById<EditText>(R.id.et_password)
        val btnRegister = view.findViewById<Button>(R.id.btn_register)
        val btnGoogleSignUp = view.findViewById<Button>(R.id.btn_google_sign_up)

        view.findViewById<TextView>(R.id.tv_login).setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        btnRegister.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val name = nameInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
                Toast.makeText(context, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = "Creating Account..."

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // 1. Dual-sync register via server (creates MongoDB user & imports to Firebase Auth/Firestore)
                    val serverRes = repository.registerWithServer(name, email, password)
                    var registered = false

                    if (serverRes.success && !serverRes.firebaseCustomToken.isNullOrEmpty()) {
                        auth.signInWithCustomToken(serverRes.firebaseCustomToken).await()
                        registered = true
                    } else if (serverRes.success) {
                        try {
                            auth.signInWithEmailAndPassword(email, password).await()
                            registered = true
                        } catch (_: Exception) {}
                    }

                    // 2. Fallback to direct Firebase Auth registration if server was unreachable
                    if (!registered) {
                        try {
                            val authRes = auth.createUserWithEmailAndPassword(email, password).await()
                            val uid = authRes.user?.uid
                            if (uid != null) {
                                val userDoc = hashMapOf(
                                    "uid" to uid,
                                    "name" to name,
                                    "email" to email,
                                    "coupleId" to null
                                )
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("users").document(uid).set(userDoc).await()
                            }
                            registered = true
                        } catch (fbEx: Exception) {
                            Toast.makeText(context, serverRes.error ?: fbEx.message ?: "Registration failed", Toast.LENGTH_LONG).show()
                            btnRegister.isEnabled = true
                            btnRegister.text = "Create Account"
                            return@launch
                        }
                    }

                    if (registered) {
                        Toast.makeText(context, "Welcome to Our Bloom, $name!", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_registerFragment_to_setupCoupleFragment)
                    }
                } catch (e: Exception) {
                    Log.e("RegisterFragment", "Registration error", e)
                    Toast.makeText(context, e.message ?: "Registration failed.", Toast.LENGTH_SHORT).show()
                    btnRegister.isEnabled = true
                    btnRegister.text = "Create Account"
                }
            }
        }

        btnGoogleSignUp.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
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
                Toast.makeText(context, "Google sign up failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val user = repository.getCurrentUser()
                            if (user?.coupleId.isNullOrEmpty()) {
                                findNavController().navigate(R.id.action_registerFragment_to_setupCoupleFragment)
                            } else {
                                findNavController().navigate(R.id.action_registerFragment_to_dashboardFragment)
                            }
                        } catch (e: Exception) {
                            findNavController().navigate(R.id.action_registerFragment_to_setupCoupleFragment)
                        }
                    }
                } else {
                    Toast.makeText(context, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
