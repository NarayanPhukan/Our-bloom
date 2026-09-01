package com.ourbloom.app.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.ourbloom.app.R

class RegisterFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register, container, false)
        auth = FirebaseAuth.getInstance()
        
        val nameInput = view.findViewById<EditText>(R.id.et_name)
        val emailInput = view.findViewById<EditText>(R.id.et_email)
        val passwordInput = view.findViewById<EditText>(R.id.et_password)

        view.findViewById<TextView>(R.id.tv_login).setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        view.findViewById<Button>(R.id.btn_register).setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val name = nameInput.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty() && name.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(requireActivity()) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(context, "Registration Successful!", Toast.LENGTH_SHORT).show()
                            // We can store the name in Firestore later
                            findNavController().navigate(R.id.action_registerFragment_to_dashboardFragment)
                        } else {
                            Toast.makeText(context, "Registration Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(context, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}
