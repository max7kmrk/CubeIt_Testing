package cz.cubeit.cubeit

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.android.synthetic.main.fragment_faction.*
import kotlinx.android.synthetic.main.fragment_faction.view.*
import kotlinx.android.synthetic.main.popup_dialog.view.*
import kotlinx.android.synthetic.main.popup_dialog_recyclerview.view.*
import kotlinx.android.synthetic.main.row_faction_members.view.*
import kotlinx.android.synthetic.main.row_faction_pictures.view.*


class Fragment_Faction: Fragment(){

    var currentInstanceOfFaction: Faction? = null
    var myFaction = true
    lateinit var viewTemp:View
    var factionID: String? = ""
    var displayX = 0.0
    var logClosed = true
    var firstLoad = true
    var chosenPictureID: String = ""
    var giveMoneyHandler = Handler()

    companion object{
        fun newInstance(id: String? = null):Fragment_Faction{
            val fragment = Fragment_Faction()
            val args = Bundle()
            args.putString("id", id)
            fragment.arguments = args
            return fragment
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        if(isAdded && isVisible && userVisibleHint)initMain()
    }

    override fun onResume() {
        super.onResume()
        if(!firstLoad)initMain()
    }

    override fun onStop() {
        super.onStop()
        firstLoad = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        logClosed = true
    }

    private fun increaseGiveMoney(){
        val temp = viewTemp.editTextFactionGold.text.toString().toIntOrNull()
        if(temp == null) viewTemp.editTextFactionGold.setText("0")

        viewTemp.editTextFactionGold.setText(if(temp ?: 0 in 0..10000000)((temp ?: 0) + 1 + (temp ?: 0) / 8).toString() else "9999999")

        giveMoneyHandler.postDelayed({
            increaseGiveMoney()
        }, 100)
    }

    private fun initMain(){
        Data.loadingStatus = LoadingStatus.LOGGING                           //procesing
        val intent = Intent(viewTemp.context, Activity_Splash_Screen::class.java)
        intent.putExtra("refreshRate", (100).toLong())

        if(Data.player.factionID != null || factionID != null){
            if(factionID == null || factionID == ""){
                if(Data.player.faction == null || Data.factionSnapshot == null || SystemFlow.factionChange){
                    startActivity(intent)
                    Data.player.loadFaction(viewTemp.context).addOnSuccessListener {    //tries to load player's faction
                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                        currentInstanceOfFaction = Data.player.faction
                        SystemFlow.factionChange = false

                        if(currentInstanceOfFaction == null){                              //player doesn't have any faction, create new
                            (activity as Activity_Faction_Base).changePage(0)
                        }else {
                            if(Data.factionSnapshot == null){
                                val db = FirebaseFirestore.getInstance()                                                        //listens to every server status change
                                val docRef = db.collection("factions").document(Data.player.factionID?.toString() ?: "")
                                Data.factionSnapshot = docRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, e ->
                                    if (e != null) {
                                        return@addSnapshotListener
                                    }
                                    if (snapshot != null && snapshot.exists()) {
                                        val newFaction = snapshot.toObject(Faction::class.java)
                                        if(newFaction == null) activity?.finish()
                                        newFaction?.actionLog?.sortByDescending { it.captured }
                                        Data.player.faction?.actionLog?.sortByDescending { it.captured }

                                        if(Data.player.faction != null && Data.player.faction == newFaction){
                                            currentInstanceOfFaction = Data.player.faction
                                        }else {
                                            if(newFaction?.actionLog != currentInstanceOfFaction?.actionLog){
                                                Data.factionLogChanged = true
                                                viewTemp.imageViewFactionLogNew.visibility = View.VISIBLE
                                            }

                                            currentInstanceOfFaction = newFaction
                                            Data.player.faction = newFaction
                                        }
                                        if(isAdded && isVisible)init()
                                        if(Data.player.faction != newFaction && !isVisible){
                                            SystemFlow.factionChange = true
                                        }

                                        (viewTemp.listViewFactionMembers.adapter as FactionMemberList).notifyDataSetChanged()

                                    }
                                }
                            }
                            init()
                        }

                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                    }.addOnFailureListener {
                        activity?.finish()
                        SystemFlow.factionChange = false
                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                    }
                }else {
                    currentInstanceOfFaction = Data.player.faction
                    myFaction = true
                    init()
                    Data.loadingStatus = LoadingStatus.CLOSELOADING
                }
            }else {
                startActivity(intent)

                FirebaseFirestore.getInstance().collection("factions").document(factionID.toString()).get().addOnSuccessListener {
                    currentInstanceOfFaction = it.toObject(Faction::class.java)
                    if(currentInstanceOfFaction != null){
                        myFaction = false
                        init()
                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                    }else {
                        Data.loadingStatus = LoadingStatus.CLOSELOADING
                        activity?.finish()
                    }
                }
            }
        }
    }

    private fun init() {
        if(currentInstanceOfFaction != null) {
            viewTemp.listViewFactionMembers.adapter = FactionMemberList(currentInstanceOfFaction!!, viewTemp.textViewFactionMemberDesc, currentInstanceOfFaction!!.members[Data.player.username], myFaction, mutableListOf(), activity!!, this)
            (viewTemp.listViewFactionMembers.adapter as FactionMemberList).notifyDataSetChanged()
            if (!myFaction && Data.player.factionID != null && Data.player.factionRole == FactionRole.LEADER) {
                viewTemp.buttonFactionAlly.visibility = View.VISIBLE
                viewTemp.buttonFactionEnemy.visibility = View.VISIBLE
                viewTemp.buttonFactionInvade.visibility = View.VISIBLE
            } else if (!myFaction && Data.player.factionID == null) {
                viewTemp.buttonFactionApply.visibility = View.VISIBLE
            } else {
                viewTemp.buttonFactionAlly.visibility = View.GONE
                viewTemp.buttonFactionEnemy.visibility = View.GONE
                viewTemp.buttonFactionInvade.visibility = View.GONE
                viewTemp.buttonFactionApply.visibility = View.GONE
            }

            (activity as Activity_Faction_Base).tabLayoutFactionTemp.visibility =
                    if (myFaction && (Data.player.factionRole == FactionRole.LEADER || Data.player.factionRole == FactionRole.MODERATOR)) {
                        View.VISIBLE
                    } else View.GONE

            viewTemp.textViewFactionInfoDesc.setHTMLText(currentInstanceOfFaction?.getInfoDesc() ?: "")
            viewTemp.textViewFactionDescription.setHTMLText(currentInstanceOfFaction?.description ?: "")
            viewTemp.textViewFactionTitle.setHTMLText(currentInstanceOfFaction?.name ?: "")
            viewTemp.textViewFactionTitle.fontSizeType = CustomTextView.SizeType.title

            if(myFaction){
                viewTemp.imageViewFactionLogNew.visibility = if(Data.factionLogChanged){
                    View.VISIBLE
                }else {
                    View.GONE
                }

                viewTemp.textViewFactionGold.text = resources.getString(R.string.faction_gold, (currentInstanceOfFaction?.gold ?: 0).toString())
                viewTemp.textViewFactionMemberDesc.setHTMLText(currentInstanceOfFaction?.getMemberDesc(Data.player.username) ?: "")

                childFragmentManager.beginTransaction().replace(R.id.frameLayoutFactionLog, Fragment_Faction_Log.newInstance(currentInstanceOfFaction!!)).commitAllowingStateLoss()

                viewTemp.imageViewFactionGoldPlus.setOnTouchListener(object: Class_HoldTouchListener(viewTemp.imageViewFactionGoldPlus, false, 0f, false){

                    override fun onStartHold(x: Float, y: Float) {
                        super.onStartHold(x, y)
                        increaseGiveMoney()
                    }

                    override fun onCancelHold() {
                        super.onCancelHold()
                        giveMoneyHandler.removeCallbacksAndMessages(null)
                    }
                })

                viewTemp.buttonFactionGoldOk.setOnClickListener {
                    if (!viewTemp.editTextFactionGold.text.isNullOrEmpty()) {
                        val amount: Int = viewTemp.editTextFactionGold.text.toString().toIntOrNull() ?: 0
                        if (Data.player.gold >= amount && amount > 0) {
                            viewTemp.editTextFactionGold.setBackgroundResource(0)
                            Data.player.gold -= amount

                            currentInstanceOfFaction!!.members[Data.player.username]!!.goldGiven = amount.toLong()
                            currentInstanceOfFaction!!.gold += amount
                            currentInstanceOfFaction!!.actionLog.add(FactionActionLog(Data.player.username, " donated ", "$amount gold"))
                            currentInstanceOfFaction?.upload()
                        } else{
                            SystemFlow.vibrateAsError(viewTemp.context)
                            it.startAnimation(AnimationUtils.loadAnimation(viewTemp.context, R.anim.animation_shaky_short))
                        }
                    } else{
                        SystemFlow.vibrateAsError(viewTemp.context)
                        it.startAnimation(AnimationUtils.loadAnimation(viewTemp.context, R.anim.animation_shaky_short))
                    }
                }
                //if(currentInstanceOfFaction!!.contains(Data.player.username))view.textViewFactionMemberInfo.setHTMLText(currentInstanceOfFaction!!.getMemberDesc(currentInstanceOfFaction!!.members.indexOf(currentInstanceOfFaction!!.members.findMember(Data.player.username))))
                //view.textViewFactionMemberInfo.performClick()
            }else {
                viewTemp.imageViewFactionOpenLog.visibility = View.GONE
                viewTemp.imageViewFactionLogNew.visibility = View.GONE
                viewTemp.textViewFactionMemberDesc.setHTMLText(currentInstanceOfFaction!!.getMemberDesc(currentInstanceOfFaction!!.leader))

                viewTemp.buttonFactionGoldOk.visibility = View.GONE
                viewTemp.imageViewFactionGoldPlus.visibility = View.GONE
                viewTemp.textViewFactionGold.visibility = View.GONE
                viewTemp.editTextFactionGold.visibility = View.GONE
                (activity as Activity_Faction_Base).tabLayoutFactionTemp.visibility = View.GONE
            }


            viewTemp.buttonFactionAlly.visibility = if(Data.player.faction != null && !Data.player.faction!!.pendingInvitationsFaction.containsKey(currentInstanceOfFaction!!.id.toString()) && !myFaction && (Data.player.factionRole == FactionRole.LEADER || Data.player.factionRole == FactionRole.MODERATOR) && currentInstanceOfFaction!!.id != Data.player.factionID){
                View.VISIBLE
            }else View.GONE

            viewTemp.buttonFactionEnemy.visibility = if(Data.player.faction != null && !Data.player.faction!!.enemyFactions.containsKey(currentInstanceOfFaction!!.id.toString()) && !myFaction && Data.player.factionRole == FactionRole.LEADER && currentInstanceOfFaction!!.id != Data.player.factionID){
                View.VISIBLE
            }else View.GONE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {             //arguments: ÍD - loads faction by its id
        super.onCreate(savedInstanceState)
        factionID = arguments?.getString("id")
        viewTemp = inflater.inflate(R.layout.fragment_faction, container, false)
        if(Data.player.factionID != null || factionID != null)initMain()

        val dm = DisplayMetrics()
        val windowManager = viewTemp.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(dm)
        displayX = dm.widthPixels.toDouble()
        viewTemp.frameLayoutFactionLog.layoutParams.width = (displayX * 0.34).toInt()

        viewTemp.imageViewFactionOpenLog.setOnClickListener { view ->
            view.isEnabled = false
            view.isClickable = false
            viewTemp.imageViewFactionLogNew.visibility = View.GONE
            Data.factionLogChanged = false
            //viewTemp.imageViewFactionOpenLog.bringToFront()

            if(logClosed){
                (activity as Activity_Faction_Base).imageViewMenuUp?.visibility = View.GONE
                ValueAnimator.ofFloat(frameLayoutFactionLog.x, frameLayoutFactionLog.x - frameLayoutFactionLog.width).apply {
                    duration = 800
                    addUpdateListener {
                        frameLayoutFactionLog.x = it.animatedValue as Float
                        viewTemp.imageViewFactionOpenLog.x = (it.animatedValue as Float - viewTemp.imageViewFactionOpenLog.width)
                    }
                    addListener(object : Animator.AnimatorListener {
                        override fun onAnimationRepeat(animation: Animator?) {
                        }

                        override fun onAnimationCancel(animation: Animator?) {
                        }

                        override fun onAnimationStart(animation: Animator?) {
                        }

                        override fun onAnimationEnd(animation: Animator?) {
                            logClosed = false
                            viewTemp.imageViewFactionOpenLog.rotation = 270f
                            view.isEnabled = true
                            view.isClickable = true
                        }

                    })
                    start()
                }
            }else {
                (activity as Activity_Faction_Base).imageViewMenuUp?.visibility = View.VISIBLE
                ValueAnimator.ofFloat(frameLayoutFactionLog.x, frameLayoutFactionLog.x + frameLayoutFactionLog.width).apply {
                    duration = 800
                    addUpdateListener {
                        frameLayoutFactionLog.x = it.animatedValue as Float
                        viewTemp.imageViewFactionOpenLog.x = (it.animatedValue as Float - viewTemp.imageViewFactionOpenLog.width)
                    }
                    addListener(object : Animator.AnimatorListener {
                        override fun onAnimationRepeat(animation: Animator?) {
                        }

                        override fun onAnimationCancel(animation: Animator?) {
                        }

                        override fun onAnimationStart(animation: Animator?) {
                        }

                        override fun onAnimationEnd(animation: Animator?) {
                            logClosed = true
                            viewTemp.imageViewFactionOpenLog.rotation = 90f
                            view.isEnabled = true
                            view.isClickable = true
                        }
                    })
                    start()
                }
            }
        }

        viewTemp.buttonFactionApply.setOnClickListener {
            if(Data.player.factionID == null && currentInstanceOfFaction?.openToAllies == true){
                var containsAlly = false

                //TODO openToAllies functionality (possible solutions: load all allies
                // from MODERATORs and LEADER, basically just exists() of some sort / manage allies inside of the faction - long term disadvantage (have to be current))
                /*for(i in currentInstanceOfFaction!!.members.values.filter_icon { it.role == FactionRole.MODERATOR || it.role == FactionRole.LEADER }) {
                    if (i.allies.contains(Data.player.username)) {
                        db.collection("factions").document(this.factionID.toString()).update(mapOf("members.${Data.player.username}" to FactionMember(Data.player.username, FactionRole.MEMBER, Data.player.level, Data.player.allies)))
                        Data.player.factionRole = FactionRole.MEMBER
                        Data.player.factionID = currentInstanceOfFaction?.id
                        Data.player.factionName = currentInstanceOfFaction?.name
                        containsAlly = true
                        activity?.finish()
                        break
                    }
                }*/
                if(!containsAlly){
                    Data.player.writeInbox(currentInstanceOfFaction!!.recruiter, InboxMessage(status = MessageStatus.Faction, receiver = currentInstanceOfFaction!!.recruiter, sender = Data.player.username, subject = "${Data.player.username} wants to discuss faction position.", content = "Greetings!\nPlayer ${Data.player.username} wants to discuss about joining your faction as a member.\n\nThis is automated message, reply to this message will be sent to ${Data.player.username}"))
                    Snackbar.make(viewTemp, "Automatic message to a recruiter was sent.", Snackbar.LENGTH_SHORT).show()
                }
            }else {
                Data.player.writeInbox(currentInstanceOfFaction!!.recruiter, InboxMessage(status = MessageStatus.Faction, receiver = currentInstanceOfFaction!!.recruiter, sender = Data.player.username, subject = "${Data.player.username} wants to discuss faction position.", content = "Greetings!\nPlayer ${Data.player.username} wants to discuss about joining your faction as a member.\n\nThis is automated message, reply to this message will be sent to ${Data.player.username}"))
                Snackbar.make(viewTemp, "Automatic message to a recruiter was sent.", Snackbar.LENGTH_SHORT).show()
            }
            viewTemp.buttonFactionApply.isEnabled = false
        }

        viewTemp.buttonFactionAlly.setOnClickListener {
            if(currentInstanceOfFaction != null && Data.player.factionRole == FactionRole.LEADER && Data.player.faction != null){
                val db = FirebaseFirestore.getInstance()

                Data.player.writeInbox(currentInstanceOfFaction?.leader ?: "", InboxMessage(status = MessageStatus.Faction, receiver = currentInstanceOfFaction!!.leader, sender = Data.player.username, subject = "${Data.player.username} wants to ally with your faction.", content = "Greetings!\nPlayer ${Data.player.username} from faction ${Data.player.factionName} wants to discuss about being ally with your faction.\n\nThis is automated message, reply to this message will be sent to ${Data.player.username}", isInvitation1 = true, invitation = Invitation("","","", InvitationType.factionAlly, Data.player.factionID!!, "")))
                db.collection("factions").document(Data.player.factionID!!.toString()).update(mapOf("pendingInvitationsFaction.${currentInstanceOfFaction!!.id.toString()}" to currentInstanceOfFaction!!.name))
                Data.player.faction!!.pendingInvitationsFaction[currentInstanceOfFaction!!.id.toString()] = currentInstanceOfFaction!!.name
                Snackbar.make(viewTemp, "Ally request was successfully sent, wait for their response.", Snackbar.LENGTH_SHORT).show()
                viewTemp.buttonFactionAlly.isEnabled = false
            }else Snackbar.make(viewTemp, "Failed loading the faction.", Snackbar.LENGTH_SHORT).show()
        }

        viewTemp.buttonFactionEnemy.setOnClickListener {
            if(currentInstanceOfFaction != null && Data.player.factionRole == FactionRole.LEADER){

                val viewP = layoutInflater.inflate(R.layout.popup_dialog, container, false)
                val window = PopupWindow(context)
                viewP.textViewDialogInfo.text = "Do you really want to put ${currentInstanceOfFaction!!.name} on your faction's enemy list?"
                window.isOutsideTouchable = false
                window.isFocusable = true
                val db = FirebaseFirestore.getInstance()
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                viewP.buttonDialogAccept.setOnClickListener {      //TODO use methods
                    db.collection("factions").document(Data.player.factionID!!.toString()).update(mapOf("enemyFactions.${currentInstanceOfFaction!!.id.toString()}" to currentInstanceOfFaction!!.name))
                    db.collection("factions").document(currentInstanceOfFaction!!.name).update(mapOf("enemyFactions.${Data.player.factionID.toString()}" to Data.player.factionName))
                    Data.player.faction!!.enemyFactions[currentInstanceOfFaction!!.id.toString()] = currentInstanceOfFaction!!.name
                    Data.player.writeInbox(currentInstanceOfFaction!!.leader, InboxMessage(status = MessageStatus.Faction, receiver = currentInstanceOfFaction!!.leader, sender = Data.player.username, subject = "${Data.player.factionName} put your faction on their enemy list!", content = "Greetings!\nPlayer ${Data.player.username} from faction ${Data.player.factionName} just put you on their faction's enemy list.\nYou gotta do something!"))
                    Snackbar.make(viewTemp, "Faction successfully added to your enemies.", Snackbar.LENGTH_SHORT).show()

                    viewTemp.buttonFactionEnemy.isEnabled = false
                    window.dismiss()
                }
                viewP.imageViewDialogClose.setOnClickListener {
                    window.dismiss()
                }
                window.showAtLocation(viewP, Gravity.CENTER,0,0)

            }else Snackbar.make(viewTemp, "Failed loading the faction.", Snackbar.LENGTH_SHORT).show()
        }
        viewTemp.buttonFactionInvade.setOnClickListener {
            Snackbar.make(viewTemp, "Your faction is not advanced enough.", Snackbar.LENGTH_SHORT).show()
        }

        return viewTemp
    }


    private class FactionMemberList(val faction: Faction, val memberDesc: CustomTextView, val playerMember: FactionMember?, val myFaction: Boolean, var members: MutableList<FactionMember> = mutableListOf(), val activity: Activity, val parentFragment: Fragment_Faction) : BaseAdapter() {

        override fun getCount(): Int {
            return members.size / 4 + 1
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItem(position: Int): Any {
            return "TEST STRING"
        }

        override fun getView(position: Int, convertView: View?, viewGroup: ViewGroup?): View {
            val rowMain: View

            if (convertView == null) {
                val layoutInflater = LayoutInflater.from(viewGroup!!.context)
                rowMain = layoutInflater.inflate(R.layout.row_faction_members, viewGroup, false)
                val viewHolder = ViewHolder(rowMain.imageViewFactionRow0, rowMain.imageViewFactionRow1, rowMain.imageViewFactionRow2, rowMain.imageViewFactionRow3,
                        rowMain.textViewFactionRow0, rowMain.textViewFactionRow1, rowMain.textViewFactionRow2, rowMain.textViewFactionRow3,
                        rowMain.imageViewFactionRowBadge0, rowMain.imageViewFactionRowBadge1, rowMain.imageViewFactionRowBadge2, rowMain.imageViewFactionRowBadge3)
                rowMain.tag = viewHolder
            } else {
                rowMain = convertView
            }
            val viewHolder = rowMain.tag as ViewHolder

            val rowIndex:Int = if(position == 0) 0 else{
                position*4
            }
            var member: FactionMember


            members.addAll(faction.members.values)
            members.sortByDescending { it.role.ordinal }

            class Node(
                    val img: ImageView,
                    val txt: CustomTextView,
                    val badge: ImageView,
                    val index: Int = 0,
                    val myFaction: Boolean = false
            ){
                var isEnabled: Boolean = true
                var visible: Boolean = true

                init {
                    initialize()
                }

                fun initialize(){
                    if((rowIndex + index) < members.size){

                        if(members.find { it.role == FactionRole.LEADER }?.username == Data.player.username){
                            val givenGoldDay = members[rowIndex + index].goldGiven.toInt().safeDivider(members[rowIndex + index].membershipLength)

                            img.setBackgroundResource(when {
                                givenGoldDay > faction.taxPerDay -> R.color.itemborder_uncommon
                                givenGoldDay < faction.taxPerDay -> R.color.progress_hp
                                else -> 0
                            })
                        }
                        val dr: RoundedBitmapDrawable = RoundedBitmapDrawableFactory.create(parentFragment.resources, members[rowIndex + index].profilePicture)
                        dr.cornerRadius = 15f
                        dr.isCircular = true
                        img.setImageDrawable(dr)
                        txt.setHTMLText(members[rowIndex + index].getShortDesc())
                        badge.setImageResource(members[rowIndex + index].role.getDrawable())

                        img.apply {
                            setOnClickListener {
                                member = members[rowIndex + index]
                                memberDesc.setHTMLText(faction.getMemberDesc(member.username))
                            }

                            setOnLongClickListener {
                                member = members[rowIndex + index]
                                showMenu(it, context, member, playerMember, faction, this@FactionMemberList, myFaction, activity)
                                true
                            }
                        }
                        txt.apply {
                            setOnClickListener {
                                img.performClick()
                            }

                            setOnLongClickListener {
                                img.performLongClick()
                            }
                        }
                        badge.apply {
                            setOnClickListener {
                                img.performClick()
                            }

                            setOnLongClickListener {
                                img.performLongClick()
                            }
                        }
                    }else{
                        enabled(false)
                        visibility(false)
                    }
                }

                fun enabled(boolean: Boolean): Boolean{
                    img.isEnabled = boolean
                    txt.isEnabled = boolean
                    badge.isEnabled = boolean
                    isEnabled = boolean
                    return isEnabled
                }

                fun visibility(boolean: Boolean): Boolean{
                    if(boolean){
                        img.visibility = View.VISIBLE
                        txt.visibility = View.VISIBLE
                        badge.visibility = View.VISIBLE
                    }else {
                        img.visibility = View.GONE
                        txt.visibility = View.GONE
                        badge.visibility = View.GONE
                    }
                    visible = boolean
                    return visible
                }
            }

            val node0: Node = Node(viewHolder.imgMember0, viewHolder.txtMember0, viewHolder.badge0, 0, myFaction)
            val node1: Node = Node(viewHolder.imgMember1, viewHolder.txtMember1, viewHolder.badge1, 1, myFaction)
            val node2: Node = Node(viewHolder.imgMember2, viewHolder.txtMember2, viewHolder.badge2, 2, myFaction)
            val node3: Node = Node(viewHolder.imgMember3, viewHolder.txtMember3, viewHolder.badge3, 3, myFaction)

            return rowMain
        }
        private class ViewHolder(val imgMember0: ImageView, val imgMember1: ImageView, val imgMember2: ImageView, val imgMember3: ImageView,
                                 val txtMember0: CustomTextView, val txtMember1: CustomTextView, val txtMember2: CustomTextView, val txtMember3: CustomTextView,
                                 val badge0: ImageView, val badge1: ImageView, val badge2: ImageView, val badge3: ImageView)

        companion object {
            fun showMenu(it: View, context: Context, member: FactionMember, playerMember: FactionMember?, faction: Faction, parent: BaseAdapter, myFaction: Boolean, activity: Activity) {

                val wrapper = ContextThemeWrapper(context, R.style.FactionPopupMenu)
                val popup = PopupMenu(wrapper, it)
                val inflater = popup.menuInflater
                inflater.inflate(R.menu.menu_faction_member, popup.menu)

                val popupMenu = popup.menu
                popupMenu.findItem(R.id.menu_faction_ally).isVisible = !Data.player.socials.any { it.username == member.username && (it.type == SocialItemType.Ally || it.type == SocialItemType.Sent) } && member.username != Data.player.username

                if(myFaction && member.username == Data.player.username){
                    popupMenu.findItem(R.id.menu_faction_message).isVisible = false
                    popupMenu.findItem(R.id.menu_faction_show_profile).isVisible = false
                    popupMenu.findItem(R.id.menu_faction_leave).isVisible = true
                    popupMenu.findItem(R.id.menu_faction_picture).isVisible = true
                }else {
                    popupMenu.findItem(R.id.menu_faction_leave).isVisible = false
                    popupMenu.findItem(R.id.menu_faction_picture).isVisible = false
                    popupMenu.findItem(R.id.menu_faction_message).isVisible = true
                    popupMenu.findItem(R.id.menu_faction_show_profile).isVisible = true
                }

                if (myFaction && playerMember != null && playerMember.compareTo(member) == 1) {
                    popupMenu.findItem(R.id.menu_faction_kick).isVisible = true
                    popupMenu.findItem(R.id.menu_faction_demote).isVisible = true
                    popupMenu.findItem(R.id.menu_faction_promote).isVisible = true
                    popupMenu.findItem(R.id.menu_faction_warn).isVisible = true
                } else {
                    popupMenu.findItem(R.id.menu_faction_kick).isVisible = false
                    popupMenu.findItem(R.id.menu_faction_demote).isVisible = false
                    popupMenu.findItem(R.id.menu_faction_promote).isVisible = false
                    popupMenu.findItem(R.id.menu_faction_warn).isVisible = false
                }

                var bitmapId = ""
                popup.setOnMenuItemClickListener { menuItem ->
                    when(menuItem.title){
                        "Message" -> {
                            val intent = Intent(context, Activity_Inbox()::class.java)
                            intent.putExtra("receiver", member.username)
                            context.startActivity(intent)
                        }
                        "Ally" -> {
                            menuItem.isVisible = false
                            Data.player.requestSocialAlly(member.username, member.profilePictureID, context)
                        }
                        "Show profile" -> {
                            val intent = Intent(context, ActivityFightBoard::class.java)
                            intent.putExtra("username", member.username)
                            context.startActivity(intent)
                        }
                        "Promote" -> {
                            faction.promoteMember(member.username, Data.player.username)
                            parent.notifyDataSetChanged()
                        }
                        "Demote" -> {
                            faction.demoteMember(member.username, Data.player.username)
                            parent.notifyDataSetChanged()
                        }
                        "Kick" -> {
                            val viewP = activity.layoutInflater.inflate(R.layout.popup_dialog, null, false)
                            val window = PopupWindow(context)
                            window.contentView = viewP
                            viewP.textViewDialogInfo.text = "Do you want to kick ${member.username}?"
                            window.isOutsideTouchable = false
                            window.isFocusable = true
                            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                            viewP.buttonDialogAccept.setOnClickListener {
                                faction.kickMember(member, Data.player.username)
                                faction.members.remove(member.username)
                                parent.notifyDataSetChanged()
                                window.dismiss()
                            }
                            viewP.imageViewDialogClose.setOnClickListener {
                                window.dismiss()
                            }
                            window.showAtLocation(viewP, Gravity.CENTER,0,0)
                        }
                        "Warn" -> {
                            Data.player.writeInbox(member.username, InboxMessage(status = MessageStatus.Faction, receiver = member.username, sender = faction.name, subject = "${Data.player.username} warned you!", content = faction.warnMessage))
                            popupMenu.close()
                        }
                        "Leave" ->{
                            val viewP = activity.layoutInflater.inflate(R.layout.popup_dialog, null, false)
                            val window = PopupWindow(context)
                            window.contentView = viewP
                            viewP.textViewDialogInfo.text = "Do you want to leave your faction?"
                            window.isOutsideTouchable = false
                            window.isFocusable = true
                            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                            viewP.buttonDialogAccept.setOnClickListener {
                                viewP.buttonDialogAccept.isEnabled = false
                                Data.player.leaveFaction()
                                val intent = Intent(context, ActivityHome::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                context.startActivity(intent)
                                window.dismiss()
                                popupMenu.close()
                            }
                            viewP.imageViewDialogClose.setOnClickListener {
                                window.dismiss()
                            }
                            window.showAtLocation(viewP, Gravity.CENTER,0,0)
                        }
                        "Picture" ->{
                            val viewP = activity.layoutInflater.inflate(R.layout.popup_dialog_recyclerview, null, false)
                            val window = PopupWindow(viewP, (((activity as? SystemFlow.GameActivity)?.dm?.heightPixels ?: 1) * 1.2).toInt(), ((activity as? SystemFlow.GameActivity)?.dm?.heightPixels ?: 1))
                            window.isOutsideTouchable = false
                            window.isFocusable = true
                            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                            viewP?.buttonDialogRecyclerOk?.visibility = View.VISIBLE

                            viewP?.buttonDialogRecyclerOk?.setOnClickListener {
                                bitmapId = (viewP.recyclerViewDialogRecycler.adapter as? SystemFlow.DialogSquareBitmapPicker)?.innerChosenBitmap
                                        ?: ""
                                if(bitmapId != member.profilePictureID){
                                    faction.changeMemberProfile(member.username, bitmapId)
                                    member.profilePictureID = bitmapId
                                    parent.notifyDataSetChanged()
                                }
                                window.dismiss()
                            }
                            viewP?.recyclerViewDialogRecycler?.apply {
                                layoutManager = LinearLayoutManager(activity)
                                adapter = SystemFlow.DialogSquareBitmapPicker(activity, bitmapId, Data.storageIdentifiers.mapProfilePictures)
                            }
                            viewP?.imageViewDialogRecyclerClose?.setOnClickListener {
                                window.dismiss()
                            }
                            window.showAtLocation(viewP, Gravity.CENTER, 0, 0)

                            /*val viewP = activity.layoutInflater.inflate(R.layout.popup_dialog_listview, null, false)
                            val window = PopupWindow(context)
                            window.contentView = viewP
                            val buttonYes: Button = viewP.buttonDialogListViewOk
                            val buttonNo:ImageView = viewP.buttonCloseDialogListView
                            val listView: ListView = viewP.listViewDialogListView
                            (parent as FactionMemberList).parentFragment.chosenPictureID = member.profilePictureID

                            listView.adapter = MemberProfilePicture(parent.parentFragment)
                            window.isOutsideTouchable = false
                            window.isFocusable = true
                            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                            buttonYes.setOnClickListener {
                                faction.changeMemberProfile(member.username, parent.parentFragment.chosenPictureID)
                                window.dismiss()
                                popupMenu.close()
                                parent.notifyDataSetChanged()
                            }
                            buttonNo.setOnClickListener {
                                window.dismiss()
                            }
                            window.showAtLocation(viewP, Gravity.CENTER,0,0)*/
                        }
                    }
                    true
                }

                popup.show()
            }
        }
    }

    private class MemberProfilePicture(val parent :Fragment_Faction) : BaseAdapter() {

        override fun getCount(): Int {
            return Data.storageIdentifiers.mapProfilePictures.size / 5 + 1
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItem(position: Int): Any {
            return "TEST STRING"
        }

        override fun getView(position: Int, convertView: View?, viewGroup: ViewGroup?): View {
            val rowMain: View

            if (convertView == null) {
                val layoutInflater = LayoutInflater.from(viewGroup?.context)
                rowMain = layoutInflater.inflate(R.layout.row_faction_pictures, viewGroup, false)
                val viewHolder = ViewHolder(rowMain.imageViewFactionPicturesRow0, rowMain.imageViewFactionPicturesRow1, rowMain.imageViewFactionPicturesRow2, rowMain.imageViewFactionPicturesRow3, rowMain.imageViewFactionPicturesRow4)
                rowMain.tag = viewHolder
            } else {
                rowMain = convertView
            }
            val viewHolder = rowMain.tag as ViewHolder

            val indexAdapter:Int = if(position == 0) 0 else{
                position * 5
            }

            class Node(
                    var component: ImageView,
                    var index: Int = 0
            ){
                var pictureID: String = ""

                init {
                    if(Data.storageIdentifiers.mapProfilePictures.size > indexAdapter + index){
                        pictureID = Data.storageIdentifiers.mapProfilePictures[indexAdapter + index]

                        if(this.pictureID == parent.chosenPictureID){
                            component.setPadding(8, 8, 8, 8)
                        }else {
                            component.setPadding(0, 0, 0, 0)
                        }
                        val dr: RoundedBitmapDrawable = RoundedBitmapDrawableFactory.create(parent.resources, Data.downloadedBitmaps[Data.storageIdentifiers.mapProfilePictures[indexAdapter + index]])
                        dr.cornerRadius = 15f
                        dr.isCircular = true
                        component.setImageDrawable(dr)
                        component.isEnabled = true
                    }else {
                        component.setBackgroundResource(0)
                        component.setImageResource(0)
                        component.isEnabled = false
                    }

                    component.setOnClickListener {
                        parent.chosenPictureID = pictureID
                        this@MemberProfilePicture.notifyDataSetChanged()
                    }
                }
            }

            Node(viewHolder.picture0, 0)
            Node(viewHolder.picture1, 1)
            Node(viewHolder.picture2, 2)
            Node(viewHolder.picture3, 3)
            Node(viewHolder.picture4, 4)

            return rowMain
        }
        private class ViewHolder(val picture0: ImageView, val picture1: ImageView, val picture2: ImageView, val picture3: ImageView, val picture4: ImageView)
    }
}

