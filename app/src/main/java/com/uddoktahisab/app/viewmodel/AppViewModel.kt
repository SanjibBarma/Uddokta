package com.uddoktahisab.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uddoktahisab.app.data.model.*
import com.uddoktahisab.app.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUiState(val loading:Boolean=true,val loggedIn:Boolean=false,val data:BootstrapData?=null,val error:String?=null,val notice:String?=null,val offline:Boolean=false)
@HiltViewModel class AppViewModel @Inject constructor(private val repo:AppRepository):ViewModel(){
    private val _state=MutableStateFlow(AppUiState());val state=_state.asStateFlow()
    init{viewModelScope.launch{if(repo.hasSession())refresh()else{runCatching{repo.initialize()};_state.value=AppUiState(loading=false,offline=!repo.isOnline())}}}
    fun login(user:String,pass:String)=viewModelScope.launch{_state.value=_state.value.copy(loading=true,error=null);runCatching{repo.login(user,pass);repo.remoteBootstrap()}.onSuccess{_state.value=AppUiState(false,true,it,offline=false)}.onFailure{_state.value=AppUiState(false,false,error=it.message,offline=!repo.isOnline())}}
    fun refresh()=viewModelScope.launch{
        val cached=repo.cachedBootstrap();if(cached!=null)_state.value=AppUiState(false,true,cached,offline=!repo.isOnline())else _state.value=_state.value.copy(loading=true,error=null)
        if(repo.isOnline())runCatching{repo.remoteBootstrap()}.onSuccess{_state.value=AppUiState(false,true,it)}.onFailure{_state.value=_state.value.copy(loading=false,error=it.message)}
        else _state.value=_state.value.copy(loading=false,offline=true,error=if(cached==null)"ইন্টারনেট নেই এবং কোনো offline data পাওয়া যায়নি" else null)
    }
    fun completeProfile(fullName:String,present:String,permanent:String,phone:String,fatherPhone:String,nid:String)=action("completeProfile",mapOf("fullName" to fullName,"presentAddress" to present,"permanentAddress" to permanent,"phone" to phone,"fatherPhone" to fatherPhone,"nid" to nid))
    fun addSale(task:Task,date:String,qty:Double,price:Double,note:String)=action("addRecord",mapOf("taskId" to task.id,"date" to date,"quantity" to qty,"unitPrice" to price,"note" to note))
    fun requestChange(record:SaleRecord,qty:Double,price:Double,note:String,reason:String)=action("requestChange",mapOf("recordId" to record.id,"newQuantity" to qty,"newUnitPrice" to price,"newNote" to note,"reason" to reason))
    fun createUser(username:String,password:String)=action("createUser",mapOf("username" to username,"password" to password))
    fun assign(userId:String,taskIds:List<String>)=action("assignTasks",mapOf("userId" to userId,"taskIds" to taskIds))
    fun decide(requestId:String,approve:Boolean)=action("decideChangeRequest",mapOf("requestId" to requestId,"approve" to approve))
    private fun action(name:String,payload:Map<String,Any?>)=viewModelScope.launch{_state.value=_state.value.copy(loading=true,error=null);runCatching{repo.action(name,payload)}.onSuccess{message->val cached=repo.cachedBootstrap();_state.value=_state.value.copy(loading=false,data=cached?:_state.value.data,loggedIn=true,notice=message,offline=!repo.isOnline());if(repo.isOnline())refresh()}.onFailure{_state.value=_state.value.copy(loading=false,error=it.message)}}
    fun clearError(){_state.value=_state.value.copy(error=null,notice=null)}
    fun logout()=viewModelScope.launch{_state.value=_state.value.copy(loading=true,error=null);repo.logout();_state.value=AppUiState(loading=false)}
}
