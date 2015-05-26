package momijikawa.p2pscalaproto

/**
 * ノードの検索を担うクラス。
 * @param objective 検索目標のノードID。
 * @param self このノードのID。
 * @param successor SuccessorのノードID。
 * @param myTerritory このノードが担当だった場合の処理。
 * @param onHandling Successorが担当である場合の処理。
 * @param onForwarding 処理を転送する場合の処理。
 * @tparam A 処理の返り値の型。
 */
class NodeFinder[A](objective: TnodeID, self: TnodeID, successor: TnodeID, myTerritory: () => A, onHandling: () => A, onForwarding: () => A) {
  def judge: A = {
    val isNodeAlone = successor == self
    if (isNodeAlone || objective == self) myTerritory() else {
      if (objective belongs_between self and successor) onHandling() else onForwarding()
    }
  }
}

import akka.agent.Agent
import akka.actor.ActorContext

/**
 * 実際にChordが利用するノード検索の実装。
 * @param objective 検索目標のノードID。
 * @param state このノードの状態の[[akka.agent.Agent]]。
 * @param context 送信者の特定に必要な情報。
 */
class NodeFinderInjector(objective: TnodeID, state: Agent[ChordState])(implicit val context: ActorContext) {

  private def reply(message: IdAddressMessage) = context.sender ! message
  private val myTerritory = () => {
    reply(IdAddressMessage(idaddress = state().selfID))
  }
  private val nextNode = () => {
    // 相手がNoneを返した場合も素直にNoneを返す
    reply(IdAddressMessage(Some(state().succList.nodes.head)))
  }
  private val forward = () => {
    state().fingerList.closestPrecedingNode(objective)(state().selfID.get) match {
      case node => node.actorref.forward(FindNode(objective.getBase64))
    }
  }
  def judge() = new NodeFinder(objective, state().selfID.get, state().succList.nodes.head, myTerritory, nextNode, forward).judge
}