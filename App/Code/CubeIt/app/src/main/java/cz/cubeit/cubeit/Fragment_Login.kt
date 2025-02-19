package cz.cubeit.cubeit


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.PopupWindow
import android.widget.Toast
import com.google.android.gms.auth.api.signin.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.fragment_login.*
import kotlinx.android.synthetic.main.fragment_login.view.*
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.*
import kotlinx.android.synthetic.main.popup_dialog_register.view.*
import java.lang.ref.WeakReference
import java.util.*


class FragmentLogin : SystemFlow.GameFragment(R.layout.fragment_login, R.id.layoutFragmentLogin) {
    private val RC_SIGN_IN = 9001

    private var dialog: AlertDialog? = null
    private var popWindow: PopupWindow? = null

    private var mAuth: FirebaseAuth? = null
    lateinit var viewTemp: View
    lateinit var popView: View
    lateinit var auth: FirebaseAuth
    private var connectedTimer: TimerTask? = null
    private var playAnimation = true
    private val pumpInOfflineIcon = ValueAnimator.ofFloat(0.95f, 1f)
    private val pumpOutOfflineIcon = ValueAnimator.ofFloat(1f, 0.95f)
    private var passwordShown = false

    private var mGoogleSignInClient: GoogleSignInClient? = null

    var loadingAnimation: ObjectAnimator? = null

    fun isConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    override fun onStop() {
        super.onStop()
        loadingAnimation?.cancel()
        connectedTimer?.cancel()
    }

    fun onOfflineStatus(){
        System.gc()
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        viewTemp.imageViewLoginOfflineEncyclopedia.setImageBitmap(BitmapFactory.decodeResource(resources, R.drawable.book_open, opts))
        viewTemp.textViewLoginOfflineInfo.text = "Offline mode"
        viewTemp.textViewLoginOfflineInfo.setTextColor(Color.RED)
        playAnimation = true
        pumpInOfflineIcon.start()
    }

    fun onOnlineStatus(){
        System.gc()
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        viewTemp.imageViewLoginOfflineEncyclopedia.setImageResource(R.drawable.book_icon_press)
        viewTemp.textViewLoginOfflineInfo.text = "Online mode"
        viewTemp.textViewLoginOfflineInfo.setTextColor(Color.GREEN)
        playAnimation = false
        pumpInOfflineIcon.cancel()
        pumpOutOfflineIcon.cancel()
    }

    override fun onResume() {
        super.onResume()
        if(viewTemp.textViewLoginOfflineInfo.visibility == View.VISIBLE){
            connectedTimer = object : TimerTask() {
                override fun run() {
                    activity?.runOnUiThread {
                        if(!isConnected(viewTemp.context)) {
                            onOfflineStatus()
                        }else {
                            onOnlineStatus()
                        }
                    }
                }
            }
            Timer().scheduleAtFixedRate(connectedTimer, 0, 5000)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if(activity != null){
                    val tempActivity = activity as SystemFlow.GameActivity
                    firebaseAuthWithGoogle(account!!, tempActivity)
                }
            } catch (e: ApiException) {
                loadingAnimation?.cancel()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        viewTemp = super.onCreateView(inflater, container, savedInstanceState) ?: inflater.inflate(R.layout.fragment_login, container, false)
        viewTemp.loginVersionInfo.text = "Beta \tv${BuildConfig.VERSION_NAME}"
        viewTemp.loginPopUpBackground.foreground.alpha = 0

        if(activity != null){
            val opts = BitmapFactory.Options()
            opts.inScaled = false
            viewTemp.imageViewLoginForm.setImageBitmap(BitmapFactory.decodeResource(activity!!.resources, R.drawable.login_window, opts))
            viewTemp.imageViewLoginOfflineMG.setImageBitmap(BitmapFactory.decodeResource(activity!!.resources, R.drawable.minigame_icon, opts))
            viewTemp.imageViewLoginGoogleSignIn.setImageBitmap(BitmapFactory.decodeResource(activity!!.resources, R.drawable.google_sign_in, opts))
            viewTemp.imageViewLoginIcon.setImageBitmap(BitmapFactory.decodeResource(activity!!.resources, R.drawable.icon_cubeit_login, opts))
            viewTemp.imageViewLoginOfflineEncyclopedia.setImageResource(R.drawable.book_icon_press)
        }

        viewTemp.imageViewLoginOfflineMG.setOnClickListener {
            val intent = Intent(viewTemp.context, ActivityOfflineMG()::class.java)
            startActivity(intent)
        }

        viewTemp.imageViewLoginShowPass.setOnClickListener{
            viewTemp.inputPassLogin.transformationMethod = if(passwordShown){
                passwordShown = false
                null
            }else {
                passwordShown = true
                PasswordTransformationMethod()
            }
        }

        activity?.runOnUiThread {             //faster start up

            pumpInOfflineIcon.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if(playAnimation) pumpOutOfflineIcon.start()
                }
            })
            pumpOutOfflineIcon.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if(playAnimation) pumpInOfflineIcon.start()
                }
            })

            pumpInOfflineIcon.addUpdateListener {
                val value = it.animatedValue as Float
                viewTemp.imageViewLoginOfflineMG.scaleY = value
                viewTemp.imageViewLoginOfflineMG.scaleX = value
                viewTemp.imageViewLoginOfflineEncyclopedia.scaleX = value
                viewTemp.imageViewLoginOfflineEncyclopedia.scaleY = value
            }

            pumpOutOfflineIcon.addUpdateListener {
                val value = it.animatedValue as Float
                viewTemp.imageViewLoginOfflineMG.scaleY = value
                viewTemp.imageViewLoginOfflineMG.scaleX = value
                viewTemp.imageViewLoginOfflineEncyclopedia.scaleX = value
                viewTemp.imageViewLoginOfflineEncyclopedia.scaleY = value
            }


            if(!isConnected(viewTemp.context)){         //check if user is offline, if not, update UI accordingly and check his connectivity every 5 seconds
                viewTemp.textViewLoginOfflineInfo.visibility = View.VISIBLE

                connectedTimer = object : TimerTask() {
                    override fun run() {
                        activity?.runOnUiThread {
                            if(!isConnected(viewTemp.context)) {
                                viewTemp.textViewLoginOfflineInfo.text = "Offline mode"
                                viewTemp.textViewLoginOfflineInfo.setTextColor(Color.RED)
                                playAnimation = true
                                pumpInOfflineIcon.start()
                            }else {
                                viewTemp.textViewLoginOfflineInfo.text = "Online mode"
                                viewTemp.textViewLoginOfflineInfo.setTextColor(Color.GREEN)
                                playAnimation = false
                                pumpInOfflineIcon.cancel()
                                pumpOutOfflineIcon.cancel()
                            }
                        }
                    }
                }
                pumpInOfflineIcon.start()
                Timer().scheduleAtFixedRate(connectedTimer, 0, 5000)
            }else {
                pumpInOfflineIcon.cancel()
                viewTemp.textViewLoginOfflineInfo.visibility = View.GONE
                connectedTimer?.cancel()
            }

            if (SystemFlow.readFileText(viewTemp.context, "rememberMe.data") == "1") {
                viewTemp.checkBoxStayLogged.isChecked = true
                if (SystemFlow.readFileText(viewTemp.context, "emailLogin.data") != "0") viewTemp.inputEmailLogin.setText(SystemFlow.readFileText(viewTemp.context, "emailLogin.data"))
                if (SystemFlow.readFileText(viewTemp.context, "emailLogin.data") != "0") viewTemp.inputPassLogin.setText(SystemFlow.readFileText(viewTemp.context, "passwordLogin.data"))
            }

            auth = FirebaseAuth.getInstance()

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()
            mGoogleSignInClient = GoogleSignIn.getClient(viewTemp.context, gso)
            mAuth = FirebaseAuth.getInstance()

            viewTemp.buttonLoginDiffAcc.visibility = if(mAuth!!.currentUser != null){
                View.VISIBLE
            }else View.GONE

            viewTemp.buttonLoginDiffAcc.setOnClickListener {
                signOut()
                viewTemp.buttonLoginDiffAcc.visibility = View.GONE
            }

            val tempActivity = activity as SystemFlow.GameActivity

            if((activity as? ActivityLoginRegister)?.checkStoragePermission() == true){
                viewTemp.imageViewLoginGoogleSignIn.isEnabled = false
                Handler().postDelayed({
                    viewTemp.imageViewLoginGoogleSignIn.isEnabled = true
                }, 200)

                mGoogleSignInClient!!.silentSignIn().addOnSuccessListener {
                    firebaseAuthWithGoogle(it, tempActivity)
                    try {
                        Snackbar.make(viewTemp, "Welcome back!", Snackbar.LENGTH_SHORT).show()
                    }catch(e: IllegalArgumentException){
                        Toast.makeText(tempActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                    }

                }.addOnFailureListener {
                    val cm = tempActivity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    if (cm.activeNetworkInfo?.isConnected == false) Snackbar.make(viewTemp, "Your device is not connected to the internet. Please check your connection and try again.", Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewTemp.imageViewLoginGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        viewTemp.imageViewFragmentLoginLogin.setOnClickListener {
            val userEmail = viewTemp.inputEmailLogin.text.toString()
            val intentSplash = Intent(viewTemp.context, Activity_Splash_Screen::class.java)

            val userPassword = viewTemp.inputPassLogin.text.toString()
            val cm = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            Data.loadingStatus = LoadingStatus.LOGGING

            viewTemp.imageViewFragmentLoginLogin.isEnabled = false
            Handler().postDelayed({
                viewTemp.imageViewFragmentLoginLogin.isEnabled = true
            }, 200)

            if (cm.activeNetworkInfo?.isConnected == true) {

                if (viewTemp.inputEmailLogin.text?.isNotBlank() == true) {

                    if (viewTemp.inputEmailLogin.text.toString().isEmail()) {

                        if (viewTemp.inputPassLogin.text!!.isNotBlank()) {

                            startActivity(intentSplash)
                            val textView = textViewLog?.get()

                            if (userEmail.isNotEmpty() && userPassword.isNotEmpty() && GenericDB.AppInfo.appVersion <= BuildConfig.VERSION_CODE) {
                                textView?.text = resources.getString(R.string.loading_log, "Your profile information")

                                auth.signInWithEmailAndPassword(userEmail, userPassword).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        if (checkBoxStayLogged.isChecked) {
                                            SystemFlow.writeFileText(viewTemp.context, "emailLogin.data", userEmail)
                                            SystemFlow.writeFileText(viewTemp.context, "passwordLogin.data", userPassword)
                                            SystemFlow.writeFileText(viewTemp.context, "rememberMe.data", "1")
                                        } else {
                                            SystemFlow.writeFileText(viewTemp.context, "emailLogin.data", "")
                                            SystemFlow.writeFileText(viewTemp.context, "passwordLogin.data", "")
                                            SystemFlow.writeFileText(viewTemp.context, "rememberMe.data", "0")
                                        }
                                        val user = auth.currentUser

                                        if (user != null) {
                                            SignInToUserLoadData(user, viewTemp.context, this).execute()
                                        } else {
                                            showNotification("Oops", "User not found!", viewTemp.context)
                                        }

                                    } else {
                                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                                        showNotification("Oops", SystemFlow.exceptionFormatter(task.exception.toString()), viewTemp.context)
                                    }
                                }
                            } else Data.loadingStatus = LoadingStatus.CLOSELOADING

                        } else {
                            SystemFlow.vibrateAsError(viewTemp.context)
                            viewTemp.inputPassLogin.startAnimation(AnimationUtils.loadAnimation(viewTemp.context, R.anim.animation_shaky_short))
                            Snackbar.make(viewTemp, "Field required!", Snackbar.LENGTH_SHORT).show()
                        }
                    } else {
                        SystemFlow.vibrateAsError(viewTemp.context)
                        viewTemp.inputEmailLogin.startAnimation(AnimationUtils.loadAnimation(viewTemp.context, R.anim.animation_shaky_short))
                        Snackbar.make(viewTemp, "Not valid email!", Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    SystemFlow.vibrateAsError(viewTemp.context)
                    viewTemp.inputEmailLogin.startAnimation(AnimationUtils.loadAnimation(viewTemp.context, R.anim.animation_shaky_short))
                    Snackbar.make(viewTemp, "Field required!", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                SystemFlow.vibrateAsError(viewTemp.context)
                viewTemp.imageViewFragmentLoginLogin.startAnimation(AnimationUtils.loadAnimation(viewTemp.context, R.anim.animation_shaky_short))
                Handler().postDelayed({ Snackbar.make(viewTemp, "Your device is not connected to the internet. Please check your connection and try again.", Snackbar.LENGTH_SHORT).show() }, 50)
                connectedTimer?.cancel()

                if(!pumpInOfflineIcon.isRunning && !pumpOutOfflineIcon.isRunning) pumpInOfflineIcon.start()
                viewTemp.textViewLoginOfflineInfo.visibility = View.VISIBLE
                connectedTimer = object : TimerTask() {
                    override fun run() {
                        activity?.runOnUiThread {
                            if(!isConnected(viewTemp.context)) {
                                viewTemp.textViewLoginOfflineInfo.text = "Offline mode"
                                viewTemp.textViewLoginOfflineInfo.setTextColor(Color.RED)
                                playAnimation = true
                                if(!pumpInOfflineIcon.isRunning && !pumpOutOfflineIcon.isRunning) pumpInOfflineIcon.start()
                            }else {
                                viewTemp.textViewLoginOfflineInfo.text = "Online mode"
                                viewTemp.textViewLoginOfflineInfo.setTextColor(Color.GREEN)
                                playAnimation = false
                                pumpInOfflineIcon.cancel()
                                pumpOutOfflineIcon.cancel()
                            }
                        }
                    }
                }
                Timer().scheduleAtFixedRate(connectedTimer, 0, 5000)
            }
        }

        viewTemp.resetPass.setOnClickListener {
            val userEmail = viewTemp.inputEmailLogin.text.toString()

            if (userEmail.isNotEmpty()) {
                auth.sendPasswordResetEmail(userEmail)
                showNotification("Alert", "A password reset link was sent to the above email account", viewTemp.context)
            } else {
                SystemFlow.vibrateAsError(viewTemp.context)
                viewTemp.inputEmailLogin.startAnimation(AnimationUtils.loadAnimation(viewTemp.context, R.anim.animation_shaky_short))
                Snackbar.make(viewTemp, "This action requires email. Please enter a valid email above.", Snackbar.LENGTH_SHORT).show()
            }
        }
        return viewTemp
    }

    private fun showNotification(titleInput: String, textInput: String, context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(titleInput)
        builder.setMessage(textInput)
        val dialog: AlertDialog = builder.create()
        if (isVisible && isAdded) dialog.show()
    }

    class SignInToUserLoadData (user: FirebaseUser, context: Context, val parent: FragmentLogin): AsyncTask<Int, String, String?>(){
        private val innerContext: WeakReference<Context> = WeakReference(context)
        private val innerUser = user

        override fun doInBackground(vararg params: Int?): String? {
            val context = innerContext.get() ?: SystemFlow.currentGameActivity!!

            val db = FirebaseFirestore.getInstance()
            db.collection("Server").document("Generic").get().addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.getString("Status") == "on") {
                    Data.loadGlobalData(context).addOnSuccessListener {

                        db.collection("GenericDB").document("AppInfo").get().addOnSuccessListener { itGeneric ->

                            if (itGeneric.toObject(GenericDB.AppInfo::class.java) != null) {
                                GenericDB.AppInfo.updateData(itGeneric.toObject(GenericDB.AppInfo::class.java)!!)

                                if (GenericDB.AppInfo.appVersion > BuildConfig.VERSION_CODE) {
                                    Data.loadingStatus = LoadingStatus.CLOSELOADING
                                    Handler().postDelayed({ parent.showNotification("Error", "Your version is too old, download more recent one.", context) }, 100)
                                }else {
                                    Data.player.userSession = innerUser

                                    db.collection("users").whereEqualTo("userId", innerUser.uid).limit(1)
                                            .get()
                                            .addOnSuccessListener { querySnapshot ->
                                                try {
                                                    val document: DocumentSnapshot = querySnapshot.documents[0]
                                                    Data.player.username = document.getString("username") ?: ""

                                                    Data.player.loadPlayerInstance(context).addOnSuccessListener {

                                                        if (Data.player.newPlayer) {
                                                            Data.loadingStatus = LoadingStatus.REGISTERED
                                                        } else {
                                                            Data.player.init(context)
                                                            Data.player.online = true
                                                            Data.player.uploadSpecifiedItems(mapOf("online" to true)).addOnSuccessListener {
                                                                Data.loadingStatus = LoadingStatus.LOGGED
                                                            }.addOnFailureListener {
                                                                Data.loadingStatus = LoadingStatus.CLOSELOADING
                                                                Snackbar.make(parent.viewTemp, "Oops. Request timed out", Snackbar.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }.addOnFailureListener {
                                                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                                                        Snackbar.make(parent.viewTemp, it.message ?: it.localizedMessage, Snackbar.LENGTH_LONG).show()
                                                    }

                                                } catch (e: IndexOutOfBoundsException) {
                                                    Data.loadingStatus = LoadingStatus.CLOSELOADING
                                                    innerUser.unlink("google.com")
                                                    innerUser.unlink(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD)
                                                    parent.signOut()
                                                    parent.showNotification("Oops", "We're unable to find you in our database. Are you sure you have an account?", context)
                                                }
                                            }.addOnFailureListener {
                                                Data.loadingStatus = LoadingStatus.CLOSELOADING
                                                Snackbar.make(parent.viewTemp, it.message ?: it.localizedMessage, Snackbar.LENGTH_LONG).show()
                                            }
                                }
                            }else {
                                Data.loadingStatus = LoadingStatus.CLOSELOADING
                            }
                        }.addOnFailureListener {
                            Data.loadingStatus = LoadingStatus.CLOSELOADING
                        }

                    }.addOnFailureListener {
                        parent.signOut()
                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                        Snackbar.make(parent.viewTemp, it.message ?: it.localizedMessage, Snackbar.LENGTH_LONG).show()
                    }
                }else {
                    val mySnackbar = Snackbar.make(parent.viewTemp, "Server is not available. ${if(documentSnapshot.getBoolean("ShowDsc")!!)documentSnapshot.getString("ExternalDsc") else ""}. You can check our social medias for more information and updates.", Snackbar.LENGTH_INDEFINITE)

                    class MyUndoListener : View.OnClickListener {
                        override fun onClick(v: View?) {
                            mySnackbar.dismiss()
                        }
                    }
                    mySnackbar.setAction("ok", MyUndoListener())
                    mySnackbar.show()

                    Data.loadingStatus = LoadingStatus.CLOSELOADING
                }
            }.addOnFailureListener {
                Data.loadingStatus = LoadingStatus.CLOSELOADING
                parent.signOut()
            }

            return ""
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            val context = innerContext.get()
            if (result != null){
                //do something, my result is successful
            }else {
                Toast.makeText(context, "Something went wrong! Try restarting your application", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun signInToUserX(user: FirebaseUser, context: Context) {
        Data.player.userSession = user

        val db = FirebaseFirestore.getInstance()
        db.collection("users").whereEqualTo("userId", user.uid).limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    try {
                        val document: DocumentSnapshot = querySnapshot.documents[0]
                        Data.player.username = document.getString("username") ?: ""

                        Data.player.loadPlayerInstance(context).addOnSuccessListener {

                            if (Data.player.newPlayer) {
                                Data.loadingStatus = LoadingStatus.REGISTERED
                            } else {
                                Data.player.init(context)
                                Data.player.online = true
                                Data.player.uploadSpecifiedItems(mapOf("online" to true)).addOnSuccessListener {
                                    Data.loadingStatus = LoadingStatus.LOGGED
                                }.addOnFailureListener {
                                    Data.loadingStatus = LoadingStatus.CLOSELOADING
                                    Snackbar.make(viewTemp, "Oops. Request timed out", Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        }.addOnFailureListener {
                            Data.loadingStatus = LoadingStatus.CLOSELOADING
                            Snackbar.make(viewTemp, it.message ?: it.localizedMessage, Snackbar.LENGTH_LONG).show()
                        }

                    } catch (e: IndexOutOfBoundsException) {
                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                        user.unlink("google.com")
                        user.unlink(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD)
                        signOut()
                        showNotification("Oops", "We're unable to find you in our database. Are you sure you have an account?", context)
                    }
                }.addOnFailureListener {
                    Data.loadingStatus = LoadingStatus.CLOSELOADING
                    Snackbar.make(viewTemp, it.message ?: it.localizedMessage, Snackbar.LENGTH_LONG).show()
                }
    }

    private fun signInWithGoogle() {
        val signInIntent = mGoogleSignInClient!!.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun signOut() {
        if (mAuth != null && mGoogleSignInClient != null) {
            // Firebase sign out
            mAuth!!.signOut()

            // Google sign out
            mGoogleSignInClient!!.signOut()
            viewTemp.buttonLoginDiffAcc.visibility = View.GONE
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount, activity: SystemFlow.GameActivity) {
        // [START_EXCLUDE silent]
        //showProgressDialog()
        // [END_EXCLUDE]

        if (mAuth == null) {
            signOut()
            return
        }
        viewTemp.buttonLoginDiffAcc.visibility = View.VISIBLE

        var canceled = false
        if(loadingAnimation != null) loadingAnimation?.cancel()
        loadingAnimation = SystemFlow.createLoading(activity, startAutomatically = true, cancelable = true, listener = View.OnClickListener {
            canceled = true
            signOut()
            loadingAnimation?.cancel()
        })

        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        mAuth!!.fetchSignInMethodsForEmail(acct.email!!)
                .addOnSuccessListener { result ->

                    if(canceled) return@addOnSuccessListener

                    val signInMethods = result.signInMethods
                    when {
                        signInMethods!!.contains("google.com") -> {
                            val intentSplash = Intent(activity, Activity_Splash_Screen::class.java)
                            Data.loadingStatus = LoadingStatus.LOGGING

                            mAuth?.signInWithCredential(credential)?.addOnCompleteListener(activity) { task ->
                                        if(canceled) return@addOnCompleteListener

                                        Handler().postDelayed({ loadingAnimation?.cancel() }, 100)
                                        startActivity(intentSplash)
                                        if (task.isSuccessful) {
                                            // Sign in success, update UI with the signed-in user's information
                                            val user = mAuth!!.currentUser

                                            if (user != null) {
                                                SignInToUserLoadData(user, activity, this).execute()
                                            } else {
                                                // If sign in fails, display a message to the user.
                                                Snackbar.make(viewTemp, "Authentication Failed.", Snackbar.LENGTH_SHORT).show()
                                                //updateUI(null)
                                                signOut()
                                                Data.loadingStatus = LoadingStatus.CLOSELOADING
                                            }
                                            // [START_EXCLUDE]
                                            //hideProgressDialog()
                                            // [END_EXCLUDE]
                                        }else {
                                            signOut()
                                            Data.loadingStatus = LoadingStatus.CLOSELOADING
                                            Snackbar.make(viewTemp, "Authentication Failed.", Snackbar.LENGTH_SHORT).show()
                                        }
                                    }
                        }
                        signInMethods.contains(EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD) -> {
                            signOut()
                            loadingAnimation?.cancel()
                            Snackbar.make(viewTemp, "Your account has password, thus is required in this case.", Snackbar.LENGTH_SHORT).show()
                        }
                        signInMethods.isEmpty() -> {
                            loadingAnimation?.cancel()

                            if(canceled){
                                return@addOnSuccessListener
                            }

                            val intentSplash = Intent(activity, Activity_Splash_Screen::class.java)
                            Data.loadingStatus = LoadingStatus.LOGGING
                            val viewP = layoutInflater.inflate(R.layout.popup_dialog_register, null, false)
                            popView = viewP
                            popWindow = PopupWindow(activity)
                            popWindow?.contentView = viewP
                            popWindow?.isOutsideTouchable = false
                            popWindow?.isFocusable = true
                            popWindow?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                            popWindow?.setOnDismissListener {
                                viewTemp.loginPopUpBackground.foreground.alpha = 0
                            }

                            popView.textViewPopRegisterPrivacyPolicy.apply {
                                setHTMLText("By continuing you agree with our <font color='blue'>terms of service.</font>")
                                setOnClickListener {
                                    val openURL = Intent(Intent.ACTION_VIEW)
                                    openURL.data = Uri.parse("https://cubeit.cz/privacy_policy.html")
                                    startActivity(openURL)
                                }
                            }

                            popView.switchPopRegisterInvited.setOnCheckedChangeListener { _, isChecked ->
                                popView.editTextPopRegisterInvitation.visibility = if(isChecked){
                                    View.VISIBLE
                                }else {
                                    popView.editTextPopRegisterInvitation.setHTMLText("")
                                    View.GONE
                                }
                            }

                            popView.editTextPopRegisterName.addTextChangedListener(object : TextWatcher {
                                override fun afterTextChanged(s: Editable?) {
                                }

                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                    with(popView.textViewPopRegisterLength){
                                        if(count > 16 || popView.editTextPopRegisterName.length() > 16 || count < 6 || popView.editTextPopRegisterName.length() < 6){
                                            setTextColor(Color.RED)
                                        }else setTextColor(Color.WHITE)
                                        setHTMLText("${popView.editTextPopRegisterName.length()}/16")
                                    }
                                }
                            })

                            val db = FirebaseFirestore.getInstance()
                            val docRef = db.collection("users")

                            viewP.editTextPopRegisterName.setText(acct.displayName ?: "")

                            viewP.editTextPopRegisterName.addTextChangedListener(object : TextWatcher {
                                override fun afterTextChanged(s: Editable?) {
                                    if(popWindow?.isShowing == true){
                                        if ((viewP.editTextPopRegisterName.text ?: "").isNotBlank() && viewP.editTextPopRegisterName.text?.length ?: 0 in 6..16) {
                                            docRef.document(viewP.editTextPopRegisterName.text.toString()).get().addOnSuccessListener {
                                                if (it.exists()) {
                                                    viewP.textViewPopRegisterError.visibility = View.VISIBLE
                                                    viewP.editTextPopRegisterName.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.animation_shaky_short))
                                                    SystemFlow.vibrateAsError(viewP.context)
                                                } else {
                                                    viewP.textViewPopRegisterError.visibility = View.GONE
                                                }
                                            }
                                        }/* else {
                                            SystemFlow.vibrateAsError(viewP.context)
                                            viewP.editTextPopRegisterName.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.animation_shaky_short))
                                        }*/
                                    }
                                }

                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

                                }
                            })

                            viewP.buttonCloseDialogRegister.setOnClickListener {
                                popWindow?.dismiss()
                            }

                            viewP.buttonPopRegisterYes.setOnClickListener {
                                if (viewP.editTextPopRegisterName.text!!.isNotBlank() && viewP.editTextPopRegisterName.text!!.length in 6..16) {
                                    viewP.textViewPopRegisterError.visibility = View.GONE
                                    popWindow!!.dismiss()

                                    canceled = false
                                    if(loadingAnimation != null) loadingAnimation?.cancel()
                                    loadingAnimation = SystemFlow.createLoading(activity, startAutomatically = true, cancelable = true, listener = View.OnClickListener {
                                        signOut()
                                        canceled = true
                                        loadingAnimation?.cancel()
                                    })

                                    docRef.document(viewP.editTextPopRegisterName.text.toString()).get().addOnSuccessListener { documentSnapshot ->
                                        loadingAnimation?.cancel()

                                        if(canceled) return@addOnSuccessListener

                                        if (documentSnapshot.exists() || viewP.editTextPopRegisterName.text.toString().toLowerCase(Locale.ENGLISH) == "Anonymous") {
                                            if (!popWindow!!.isShowing) {
                                                viewTemp.loginPopUpBackground.bringToFront()
                                                popWindow!!.showAtLocation(viewTemp, Gravity.CENTER, 0, 0)
                                                viewTemp.loginPopUpBackground.foreground.alpha = 150
                                            }
                                            viewP.textViewPopRegisterError.visibility = View.VISIBLE
                                            viewP.textViewPopRegisterError.text = "Given username already exists."
                                            SystemFlow.vibrateAsError(viewP.context)
                                            viewP.editTextPopRegisterName.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.animation_shaky_short))

                                        } else {
                                            viewP.textViewPopRegisterError.visibility = View.GONE
                                            val tempPlayer = Player()
                                            tempPlayer.username = viewP.editTextPopRegisterName.text.toString()
                                            //tempPlayer.userSession = user

                                            startActivity(intentSplash)

                                            mAuth!!.signInWithCredential(credential).addOnSuccessListener {
                                                val user = mAuth!!.currentUser

                                                user!!.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(viewP.editTextPopRegisterName.text.toString()).build()).continueWithTask {
                                                    tempPlayer.createPlayer(user.uid, viewP.editTextPopRegisterName.text.toString()).addOnSuccessListener {
                                                        Data.player.username = viewP.editTextPopRegisterName.text.toString()
                                                        Data.player.invitedBy = popView.editTextPopRegisterInvitation.text?.toString() ?: ""
                                                        //if(Data.player.invitedBy.isNotEmpty()) db.collection("users").document(Data.player.invitedBy).get().result?.exists()

                                                        SignInToUserLoadData(user, viewTemp.context, this).execute()

                                                    }.addOnFailureListener {
                                                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                                                    }
                                                }
                                            }.addOnFailureListener {
                                                Data.loadingStatus = LoadingStatus.CLOSELOADING
                                            }
                                        }
                                    }.addOnFailureListener {
                                        loadingAnimation?.cancel()
                                        Snackbar.make(viewTemp, "Something went wrong.", Snackbar.LENGTH_SHORT)
                                    }
                                } else {
                                    SystemFlow.vibrateAsError(viewP.context)
                                    viewP.editTextPopRegisterName.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.animation_shaky_short))
                                    viewP.textViewPopRegisterError.visibility = View.VISIBLE
                                    viewP.textViewPopRegisterError.text = getString(R.string.register_username)
                                }
                            }
                            popWindow!!.showAtLocation(viewTemp, Gravity.CENTER, 0, 0)
                            viewTemp.loginPopUpBackground.bringToFront()
                            viewTemp.loginPopUpBackground.foreground.alpha = 150
                            Handler().postDelayed({
                                viewTemp.loginPopUpBackground.foreground.alpha = 150
                            }, 50)
                        }
                    }
                }
                .addOnFailureListener {
                    Snackbar.make(viewTemp, "Oops. Something went wrong, sorry!", Snackbar.LENGTH_SHORT).show()
                    loadingAnimation?.cancel()
                }
    }
}


