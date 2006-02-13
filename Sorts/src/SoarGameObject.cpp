#include"SoarGameObject.h"
#include"SoarGameGroup.h"
#include<iostream>
#include<assert.h>
#include<string>

#include "MoveFSM.h"
//#include "MineFSM.h"

void SoarGameObject::identifyBehaviors() {
  string name = gob->bp_name();
  if (friendly && name == "worker") {
    FSM* moveBehavior = new MoveFSM();
//    FSM* mineBehavior = new MineFSM();
    registerBehavior(moveBehavior);
//    registerBehavior(mineBehavior);
    cout << "FRIENDLY WORKER" << endl;
  }
}

SoarGameObject::SoarGameObject(GameObj *g, int in_owner, bool in_friendly)
: gob(g)
{
  owner = in_owner;
  friendly = in_friendly;
  //cout << "owner: " << owner << " friend? " << friendly << endl;
  identifyBehaviors();
}

SoarGameObject::~SoarGameObject()
{
  while(!memory.empty()) {
    memory.pop();
  }

  for(map<SoarActionType, FSM*>::iterator i = behaviors.begin(); 
      i != behaviors.end(); i++) 
  {
    delete i->second;
  }
}

void SoarGameObject::registerBehavior(FSM *b)
{
  assert (behaviors.find(b->name) == behaviors.end());
  b->setGameObject(gob);
  behaviors[b->name] = b;
}


void SoarGameObject::removeBehavior(SoarActionType name)
{
  map<SoarActionType, FSM*>::iterator i = behaviors.find(name);
  if (i != behaviors.end()) {
    delete i->second;
    behaviors.erase(i);
  }
}


//template<class T>
void SoarGameObject::issueCommand(SoarActionType cmd, Vector<sint4> prms)
{
  //Whether we really want this is up for analysis
  while(!memory.empty())
    memory.pop();

  map<SoarActionType, FSM*>::iterator i = behaviors.find(cmd);
  if (i == behaviors.end()) {
    cout << "No match for command" << endl;
    return;
  }

  i->second->setParams(prms);
  memory.push(i->second);
  state = cmd;
  cout << "ACTION" << endl;
  update();
}


void SoarGameObject::update()
{
  //cout << "updated object" << endl;
  group->setStale();
 if(!memory.empty())
  if(!memory.top()->update())
  {
   memory.pop();
   if(!memory.empty())
    state = memory.top()->name;
   else
    state = SA_IDLE;
  }
}


void SoarGameObject::setGroup(SoarGameGroup *g)
{
 group = g;
}

SoarGameGroup *SoarGameObject::getGroup(void)
{
 return group;
}

SoarActionType SoarGameObject::getState()
{
 return state;
}

int SoarGameObject::getOwner() {
  return owner;
}

bool SoarGameObject::getFriendly() {
  return friendly;
}
