package com.example.teamtotest.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.teamtotest.Push
import com.example.teamtotest.R
import com.example.teamtotest.adapter.ChatListAdapter
import com.example.teamtotest.dto.FileDTO
import com.example.teamtotest.dto.MembersDTO
import com.example.teamtotest.dto.MessageDTO
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_chat.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ChatActivity : AppCompatActivity() {

    private var firebaseAuth: FirebaseAuth? = null
    private var firebaseDatabase: FirebaseDatabase? = null
    private var databaseReference: DatabaseReference? = null
    private var myAdapter: ChatListAdapter? = null

    private var PID : String? = null
    private var projectName : String? = null
    private var howManyMembers : String? = null
    private lateinit var filePath : Uri

    private var ChatMessageList: ArrayList<HashMap<String, String>> = ArrayList<HashMap<String, String>>()
    private var ChatMessageData: HashMap<String, String> = HashMap<String, String>()

    val dateFormat = SimpleDateFormat("yyyyMMddHHmmss")

    private lateinit var dbMessageeventListener : ValueEventListener
    private lateinit var members_listener: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        setSupportActionBar(chat_toolbar)   // xml에서 만든 toolbar를 이 activity의 툴바로 설정
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 뒤로가기 버튼 만들기

        val getintent = intent /*데이터 수신*/
        if(getintent!=null) {
            PID = getintent.extras!!.getString("PID")
            projectName = getintent.extras!!.getString("projectName")
            howManyMembers = getintent.extras!!.getString("howManyMembers")
            chat_toolbar.title=projectName
        }

        // adapter 연결
        myAdapter = ChatListAdapter(ChatMessageList)
        chatList_recycler_view.adapter = myAdapter
        chatList_recycler_view.setHasFixedSize(true)

        // 내 uid를 현재 있는 모든 message 객체 안에 배열에 넣는다 !!


        nav_view.setNavigationItemSelectedListener{

            when (it.itemId) {
                R.id.drawer_members -> {
                    chat_drawer.closeDrawer(GravityCompat.END)
                    chat_drawer.clearFocus()
                    intent = Intent(this, AddMemberActivity::class.java)
                    intent.putExtra("PID", PID)
                    intent.putExtra("howManyMembers", howManyMembers)
                    startActivity(intent)
                }
                R.id.drawer_schedule -> {
                    chat_drawer.closeDrawer(GravityCompat.END)
                    intent = Intent(this, ScheduleActivity::class.java)
                    intent.putExtra("PID", PID)
                    startActivity(intent)
                }
                R.id.drawer_file -> {
                    chat_drawer.closeDrawer(GravityCompat.END)
                    intent = Intent(this, FileActivity::class.java)
                    intent.putExtra("PID", PID)
                    startActivity(intent)
                }

                R.id.drawer_todo -> {
                    chat_drawer.closeDrawer(GravityCompat.END)
                    intent=Intent(this,TodoActivity::class.java)
                    intent.putExtra("PID", PID)
                    startActivity(intent)
                }
                R.id.drawer_finaltest -> {
                    // 코드 추가 해야함
                    chat_drawer.closeDrawer(GravityCompat.END)
                    intent=Intent(this,FinalTestActivity::class.java)
                    intent.putExtra("PID", PID)
                    startActivity(intent)
                }
                R.id.drawer_exit -> {
                    chat_drawer.closeDrawer(GravityCompat.END)
                    exitProject()
                }
                else -> println("NavigationBar ERROR!")
            }
            false
        }

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseDatabase = FirebaseDatabase.getInstance()


        sendButton.setOnClickListener{
            if (message.length() > 0) {
                addMessageInfoToDB()
                Push(PID.toString(), message.text.toString(),"Chat")
                message.setText("")
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when(item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.toolbar_menu -> {
                chat_drawer.openDrawer(GravityCompat.END)
                true
            }
            R.id.toolbar_file -> {
                val intent = Intent()
                intent.type = "*/*"
                intent.action = Intent.ACTION_GET_CONTENT
                startActivityForResult(Intent.createChooser(intent, "파일을 선택하세요."), 0)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()
        setListener_theNumOfMembersFromMyProjects()
        readCheckToDB()
    }

    override fun onStop() {
        Log.d("here is onStop", databaseReference.toString())
        // 리스너 삭제
        databaseReference = firebaseDatabase!!.getReference("ProjectList").child(PID.toString()).child("messageList")
        databaseReference!!.removeEventListener(dbMessageeventListener)
        databaseReference = firebaseDatabase!!.getReference("ProjectList").child(PID.toString()).child("members")
        databaseReference!!.removeEventListener(members_listener)
        super.onStop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //request코드가 0이고 OK를 선택했고 data에 뭔가가 들어 있다면
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            Log.e("data", data.toString())
            filePath = data!!.data!!
            val fileName: String? = getFileName(filePath)
            uploadFile(fileName)
        }
    }

    private fun uploadFile(filename: String?)
    { //업로드할 파일이 있으면 수행
        if (filePath != null)
        { //storage
            val storage = FirebaseStorage.getInstance()

            //storage 주소와 폴더 파일명을 지정해 준다.
            val storageRef = storage.getReferenceFromUrl("gs://teamtogether-bdfc9.appspot.com")

            storageRef.child(filename!!).putFile(filePath) //성공시
                .addOnSuccessListener { Toast.makeText(applicationContext, "업로드 완료!", Toast.LENGTH_SHORT).show() } //실패시
                .addOnFailureListener { Toast.makeText(applicationContext, "업로드 실패!", Toast.LENGTH_SHORT).show() } //진행중

        } else {
            Toast.makeText(applicationContext, "파일을 먼저 선택하세요.", Toast.LENGTH_SHORT).show()
        }
        uploadFileInfoToDB(filename!!)
    }

    private fun uploadFileInfoToDB(fileName : String){
        val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
        val uid : String = firebaseAuth.currentUser!!.uid
        val userName : String = firebaseAuth.currentUser!!.displayName!!

        val date_format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val date = date_format.format(System.currentTimeMillis())

        val fileDTO : FileDTO = FileDTO(fileName, date, uid, userName)

        databaseReference = firebaseDatabase!!.reference.child("ProjectList").child(PID.toString()).child("file").push()
        databaseReference!!.setValue(fileDTO)

    }

    private fun getFileName(uri: Uri) : String? {
        var result : String? = null
        if (uri.scheme!!.equals("content"))
        {
            val cursor : Cursor? = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result
    }

    private fun exitProject(){
        val builder = AlertDialog.Builder(this)
        builder.setTitle("던지고 나가기")
        builder.setMessage("정말로 나가시겠습니까? 학점은 보장할 수 없습니다...")
        builder.setPositiveButton("예",
            DialogInterface.OnClickListener { dialog, which ->
                val myUID = firebaseAuth!!.currentUser!!.uid

                databaseReference = firebaseDatabase!!.getReference("ProjectList").child(PID.toString())
                databaseReference!!.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        for (snapshot in dataSnapshot.children) {
                            if (snapshot.key == "members") { // memberList에서 삭제
                                val membersDTO: MembersDTO = snapshot.getValue(MembersDTO::class.java)!!
                                if (membersDTO.UID_list!!.size <= 1) {// 나 혼자 남아있었다면 프로젝트 전체 삭제
                                    firebaseDatabase!!.getReference("ProjectList").child(PID.toString())
                                        .removeValue()
                                    // 리스너 삭제
                                    databaseReference =
                                        firebaseDatabase!!.getReference("ProjectList").child(PID.toString())
                                            .child("messageList")
                                    databaseReference!!.removeEventListener(dbMessageeventListener)
                                    databaseReference =
                                        firebaseDatabase!!.getReference("ProjectList").child(PID.toString())
                                            .child("members")
                                    databaseReference!!.removeEventListener(members_listener)
                                    finish()
                                    break
                                } else { // 아니라면 memberList에서 내 정보만 삭제
                                    membersDTO.UID_list!!.remove(myUID)
                                    firebaseDatabase!!.getReference("ProjectList").child(PID.toString())
                                        .child("members").setValue(membersDTO)
                                }
                            }
                            if (snapshot.key == "messageList") {
                                for (messageSnapshot in snapshot.children) {// 읽은 사람 목록에서 나 삭제
                                    val messageDTOtoRemove: MessageDTO? =
                                        messageSnapshot.getValue(MessageDTO::class.java)
                                    if (messageDTOtoRemove!!.read!!.contains(myUID)) {
                                        messageDTOtoRemove.read!!.remove(myUID)
                                        firebaseDatabase!!.getReference("ProjectList").child(PID.toString())
                                            .child("messageList").child(messageSnapshot.key.toString())
                                            .setValue(messageDTOtoRemove)
                                    }
                                }
                            }
                        }
                    }
                    override fun onCancelled(databaseError: DatabaseError) {
                        Log.w("ExtraUserInfoActivity", "loadPost:onCancelled", databaseError.toException())
                    }
                })

                // 퇴장 메세지 저장
                val messageDTO =
                    MessageDTO(
                        firebaseAuth!!.currentUser!!.displayName+"님이 퇴장하셨습니다.",
                        "",
                        "",
                        ArrayList<String>()
                    )
                val current = Date()
                val utc = Date(current.time - Calendar.getInstance().timeZone.getOffset(current.time))
                val date_formatted = dateFormat.format(utc)

                databaseReference = firebaseDatabase!!.getReference()
                databaseReference =
                    databaseReference!!.child("ProjectList").child(PID.toString()).child("messageList").child(date_formatted)
                databaseReference!!.setValue(messageDTO)


                onStop()
                finish()
            })
        builder.setNegativeButton("아니오", DialogInterface.OnClickListener { dialog, which -> })
        builder.show()


    }

    private fun addMessageInfoToDB() {
        var isReadList: ArrayList<String> = ArrayList<String>()
        isReadList.add(firebaseAuth!!.currentUser!!.uid)
        val messageDTO =
            MessageDTO(
                message.text.toString(),
                firebaseAuth!!.currentUser!!.displayName.toString(),
                firebaseAuth!!.currentUser!!.uid,
                isReadList,
                null
            )  // 유저 이름과 메세지로 message data 만들기
        val current = Date()
        val utc = Date(current.time - Calendar.getInstance().timeZone.getOffset(current.time))

        val date_formatted = dateFormat.format(utc)

        databaseReference = firebaseDatabase!!.getReference()
        databaseReference =
            databaseReference!!.child("ProjectList").child(PID.toString()).child("messageList").child(date_formatted)
        Log.e("Time Testing--->", date_formatted)

        databaseReference!!.setValue(messageDTO)
    }

    private fun readCheckToDB() {
        databaseReference = firebaseDatabase!!.getReference("ProjectList").child(PID.toString()).child("messageList")
        databaseReference!!.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val myUID: String = firebaseAuth!!.currentUser!!.uid
                // 읽은 데이터에 나의 uid 저장
                for (snapshot in dataSnapshot.children) {
                    val messageDTO = snapshot.getValue(MessageDTO::class.java)  // 데이터를 가져와서
                    if (!messageDTO!!.read!!.contains(myUID)) { // 내 uid가 없으면! 추가해준당
                        messageDTO.read!!.add(myUID)
                        databaseReference =
                            firebaseDatabase!!.getReference("ProjectList").child(PID.toString()).child("messageList").child(snapshot.key.toString())
                        databaseReference!!.setValue(messageDTO)  // 덮어쓰기
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("ExtraUserInfoActivity", "loadPost:onCancelled",
                    databaseError.toException()
                )
            }
        })
    }

    private fun setListener_MessageData() {

        dbMessageeventListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                ChatMessageList.clear()

                // list를 보여주기 위해 db에서 데이터를 받아 adapter에 데이터 전달
                for (snapshot in dataSnapshot.children) {
                    ChatMessageData = HashMap()
                    val utc : Date = dateFormat.parse(snapshot.key)
                    val date = Date(utc.time + Calendar.getInstance().timeZone.getOffset(utc.time))
                    val date_formatted = dateFormat.format(date)
                    ChatMessageData["date"] = date_formatted

                    val messageDTO = snapshot.getValue(MessageDTO::class.java)
                    ChatMessageData["who"] = messageDTO!!.who
                    ChatMessageData["message"] = messageDTO.message
                    ChatMessageData["userUID"] = messageDTO.userUID
                    ChatMessageData["isRead"] = (Integer.parseInt(howManyMembers!!) - messageDTO.read!!.size).toString()

                    if(messageDTO.todoData != null){
                        ChatMessageData["todoName"] = messageDTO.todoData.name.toString()
                        val deadline = convertLongToTime(messageDTO.todoData.deadLine)
                        ChatMessageData["deadline"] = "$deadline 까지"
                        var performers : String = ""
                        if(messageDTO.todoData.performers_name!=null) {
                            for (name in messageDTO.todoData.performers_name!!) {
                                performers += "$name "
                            }
                            ChatMessageData["performer"] = performers
                        }else{
                            ChatMessageData["performer"] = ""
                        }
                    }

                    if(messageDTO.scheduleData!=null){
                        ChatMessageData["scheduleName"] = messageDTO.scheduleData.name.toString()
                        ChatMessageData["startDate"] = convertLongToTime(messageDTO.scheduleData.startTime)
                        ChatMessageData["endDate"] = convertLongToTime(messageDTO.scheduleData.endTime)
                    }

                    ChatMessageList.add(ChatMessageData)
                    chatList_recycler_view.scrollToPosition(ChatMessageList.size-1); // 메세지리스트의 가장 밑으로 스크롤바 위치조정! 꺄
                }
//                mergeSort(ChatMessageList!!)
                myAdapter!!.notifyDataSetChanged()
                readCheckToDB()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w("ExtraUserInfoActivity", "loadPost:onCancelled",
                    databaseError.toException()
                )
            }
        }

        databaseReference = firebaseDatabase!!.getReference("ProjectList").child(PID.toString()).child("messageList")
        databaseReference!!.addValueEventListener(dbMessageeventListener)       // Projectlist/PID/messageList 경로에 있는 데이터가 뭔가가 바뀌면 알려주는 listener 설정!
    }

    fun convertLongToTime(time: Long): String {
        val date = Date(time)
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm")
        return format.format(date)
    }

    private fun setListener_theNumOfMembersFromMyProjects() {
        members_listener = object : ValueEventListener {

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val membersDTO = dataSnapshot.getValue(MembersDTO::class.java)
                howManyMembers = membersDTO!!.UID_list!!.size.toString()
                chat_how_many_members.text = howManyMembers
                setListener_MessageData()

            }

            override fun onCancelled(p0: DatabaseError) {
                Log.d("ExtraUserInfoActivity", "loadPost:onCancelled")
            }

        }
        databaseReference = firebaseDatabase!!.getReference("ProjectList").child(PID.toString()).child("members")
        databaseReference!!.addValueEventListener(members_listener)       // Projectlist 경로에 있는 데이터가 뭔가가 바뀌면 알려주는 listener 설정!
    }

    fun detectDate(messageData : HashMap<String,String>): Long {
        val tmpDate : String = messageData["date"]!!
        return tmpDate.toLong()
    }

//     소팅 안하고, DB에 오름차순으로 올라가도록 세팅. 혹시모르니 코드는 안지우고 주석처리 해둠.  -----------------------------------------------
//    fun mergeSort(arr: ArrayList<java.util.HashMap<String, String>>) {
//        sort(arr, 0, arr.size)
////        Log.e("Size---->", (arr.size-1).toString())
//        ChatMessageList = arr
//    }
//
//    private fun sort(arr: ArrayList<java.util.HashMap<String, String>>, low: Int, high: Int) {
//        if (high - low < 2) {
//            return
//        }
//        val mid = (low + high) / 2
//        sort(arr, 0, mid)
//        sort(arr, mid, high)
//        merge(arr, low, mid, high)
////        Log.e("Sorting---->", arr.toString())
//    }
//
//    private fun merge(arr: ArrayList<HashMap<String, String>>, low: Int, mid: Int, high: Int) {
//        val temp = ArrayList<HashMap<String, String>>()
////        Log.e("merging---->", (high - low).toString())
//        var t = 0
//        var l = low
//        var h = mid
//        while (l < mid && h < high) {
//            if (detectDate(arr[l]) < detectDate(arr[h])) {
//                temp.add(arr[l++])
//            } else {
//                temp.add(arr[h++])
//            }
//        }
//        while (l < mid) {
//            temp.add(arr[l++])
//        }
//        while (h < high) {
//            temp.add(arr[h++])
//        }
//        for (i in low until high) {
//            arr[i] = temp[i - low]
//        }
//
//    }

}

