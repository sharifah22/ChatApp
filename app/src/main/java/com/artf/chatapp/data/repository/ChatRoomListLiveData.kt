package com.artf.chatapp.data.repository

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.artf.chatapp.data.model.Chat
import com.artf.chatapp.data.model.Message
import com.artf.chatapp.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot

class ChatRoomListLiveData : MutableLiveData<List<Chat>>() {

    private val firebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val dbRefChatRooms by lazy { firebaseFirestore.collection("chatRooms") }
    private val dbRefUsers by lazy { firebaseFirestore.collection("users") }

    private var userChatRoomsLr: ListenerRegistration? = null

    private var userObserver = Observer<User?> { }
    private var newMsgObserver = Observer<Message?> {
        sortChatRooms()
    }

    private var user: User? = null

    private val snapshotListener = object : EventListener<QuerySnapshot> {
        override fun onEvent(
            querySnapshot: QuerySnapshot?,
            firebaseFirestoreException: FirebaseFirestoreException?
        ) {
            firebaseFirestoreException?.let { return@onEvent }
            val chatRoomList = querySnapshot?.toObjects(Chat::class.java)
            loadChatRooms(chatRoomList as List<Chat>)
            value = chatRoomList
        }
    }

    fun setNewDocRef(user: User) {
        if (this.user == user) return
        onInactive()
        this.user = user
        onActive()
    }

    private fun getChatRoomListRef(): CollectionReference {
        return dbRefUsers.document(user?.userId!!).collection("chatRooms")
    }

    fun loadChatRooms(chatRoomList: List<Chat>) {
        for (chat in chatRoomList) {
            val isReceiver = chat.receiverId != user?.userId
            val receiverId = if (isReceiver) chat.receiverId else chat.senderId
            receiverId?.let { chat.user = ReceiverLiveData(getReceiver(receiverId)) }
            chat.chatId?.let { chat.message = MessageLiveData(getSingleMsgListener(it)) }
            chat.user?.observeForever(userObserver)
            chat.message?.observeForever(newMsgObserver)
        }
    }

    private fun getReceiver(receiverId: String): DocumentReference {
        return dbRefUsers.document(receiverId)
    }

    private fun getSingleMsgListener(chatId: String): Query {
        return dbRefChatRooms.document(chatId).collection("chatRoom")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
    }

    private fun sortChatRooms() {
        val sortedList = value?.sortedByDescending {
            val timestamp = it.message?.value?.timestamp
            if (timestamp is Timestamp) timestamp.seconds else 0
        }
        value = sortedList
    }

    override fun onActive() {
        user ?: return
        userChatRoomsLr = getChatRoomListRef().addSnapshotListener(snapshotListener)
    }

    override fun onInactive() {
        userChatRoomsLr?.remove()
        userChatRoomsLr = null
        value?.let {
            for (chat in it) {
                chat.user?.removeObserver(userObserver)
                chat.message?.removeObserver(newMsgObserver)
            }
        }
    }
}