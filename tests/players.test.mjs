import test from 'node:test';import assert from 'node:assert/strict';import {setupPlayers,skinUrl} from '../core/web/js/players.js';
function element(){const classes=new Set();return {children:[],style:{},attributes:{},textContent:'',classList:{toggle(n,v){if(v)classes.add(n);else classes.delete(n)},contains(n){return classes.has(n)}},append(...nodes){for(const n of nodes){n.remove?.();n.parentElement=this;this.children.push(n)}},remove(){if(this.parentElement){const p=this.parentElement;p.children=p.children.filter(n=>n!==this);this.parentElement=null}},replaceChildren(){for(const n of this.children)n.parentElement=null;this.children=[]},setAttribute(k,v){this.attributes[k]=v}}}
test('faces follow group and proximity membership and white outline follows playback events',()=>{
 const elements=new Map(),handlers={};const el=id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id)};
 const document={getElementById:el,createElement:element};const socket={addEventListener(n,f){handlers[n]=f}};let activity;
 const stop=setupPlayers(socket,{onVoiceActivity(f){activity=f}},document);
 handlers.statusChange({connected:true});
 const self={id:'aaa-aaa',name:'Me',self:true,connected:true,edition:'Bedrock'},friend={id:'bbb-bbb',name:'Friend',connected:true,edition:'Java',skin:'https://textures.minecraft.net/texture/'+'a'.repeat(64)};
 const state={current:{name:'Team'},self,members:[self],nearby:[friend]};
 handlers.message({packetType:'groups',payload:state});assert.equal(el('group-members').children.length,1);assert.equal(el('nearby-players').children.length,1);
 const card=el('nearby-players').children[0],face=card.children[0];assert.equal(face.classList.contains('speaking'),false);
 activity({streamId:'bbbbbb',speaking:true});assert.equal(face.classList.contains('speaking'),true);assert.equal(card.children[2].textContent,'Speaking');
 handlers.message({packetType:'groups',payload:state});assert.equal(el('nearby-players').children[0],card);assert.equal(face.classList.contains('speaking'),true);
 activity({streamId:'bbbbbb',speaking:false});assert.equal(face.classList.contains('speaking'),false);
 activity({streamId:'self',speaking:true});assert.equal(el('group-members').children[0].children[0].classList.contains('speaking'),true);
 handlers.message({packetType:'groups',payload:{...state,members:[self,friend],nearby:[]}});assert.equal(el('nearby-players').children.length,0);assert.equal(el('group-members').children[1],card);
 handlers.message({packetType:'groups',payload:{...state,nearby:[]}});assert.equal(el('group-members').children.length,1);
 activity({type:'reset'});assert.equal(el('group-members').children[0].children[0].classList.contains('speaking'),false);
 stop();assert.equal(el('group-members').children.length,0);
});
test('skin URLs allow only public Minecraft texture hashes',()=>{
 assert.equal(skinUrl('https://textures.minecraft.net/texture/'+'a'.repeat(64)).length>0,true);
 for(const v of ['javascript:alert(1)','https://evil.example/skin','https://textures.minecraft.net.evil/texture/abc','http://textures.minecraft.net/texture/'+'a'.repeat(64)])assert.equal(skinUrl(v),'');
});
