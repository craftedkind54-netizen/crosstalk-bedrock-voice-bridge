import test from 'node:test';
import assert from 'node:assert/strict';
import { setupGroups } from '../core/web/js/groups.js';
test('website renders invites safely and sends join, accept, decline, create and invite actions',()=>{
 const elements=new Map();
 function element(){return {value:'',textContent:'',children:[],listeners:{},replaceChildren(){this.children=[]},append(v){this.children.push(v)},addEventListener(n,f){this.listeners[n]=f}}}
 const document={getElementById(id){if(!elements.has(id))elements.set(id,element());return elements.get(id)},createElement:element};
 const handlers={},sent=[];const socket={isConnected:()=>true,ws:{send:s=>sent.push(JSON.parse(s))},addEventListener(n,f){handlers[n]=f}};
 const stop=setupGroups(socket,document);const el=id=>document.getElementById(id);
 try{
  handlers.statusChange({connected:true});assert.equal(sent[0].action,'list');
  handlers.message({packetType:'groups',payload:{current:{id:'one',name:'Team'},groups:[{id:'one',name:'<script>bad</script>',locked:true}],players:[{id:'bob',name:'Bob'}],invites:[{id:'two',from:'Alice',name:'Second'}]}});
  assert.equal(el('group-list').children[1].textContent,'<script>bad</script> · password');
  assert.equal(el('current-group').textContent,'Group: Team');
  const row=el('group-invites').children[0];row.children[1].listeners.click();assert.equal(sent.at(-1).action,'accept');assert.equal(sent.at(-1).id,'two');
  row.children[2].listeners.click();assert.equal(sent.at(-1).action,'decline');
  el('group-list').value='one';el('group-password').value='secret';el('join-group').listeners.click();assert.deepEqual(sent.at(-1),{type:'groups',action:'join',id:'one',password:'secret'});assert.equal(el('group-password').value,'');
  el('player-list').value='bob';el('invite-player').listeners.click();assert.equal(sent.at(-1).player,'bob');
  el('new-group-name').value='New';el('create-group').listeners.click();assert.equal(sent.at(-1).name,'New');
  handlers.message({packetType:'groups',payload:{error:'Wrong password'}});assert.equal(el('group-message').textContent,'Wrong password');assert.equal(el('current-group').textContent,'Group: Team');
  el('leave-group').listeners.click();assert.equal(sent.at(-1).action,'leave');
  handlers.statusChange({connected:false});const count=sent.length;el('join-group').listeners.click();assert.equal(sent.length,count);
 }finally{stop()}
});
