package spray.can.websocket

import akka.io.Tcp
import spray.can.websocket.frame.DataFrame
import spray.can.websocket.frame.FrameRender
import spray.can.websocket.frame.Opcode
import spray.io.PipelineContext
import spray.io.Pipelines
import spray.io.PipelineStage

object FrameRendering {

  def apply(maskingKeyGen: Option[() => Array[Byte]], state: HandshakeSuccess) = new PipelineStage {
    def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
      def maskingKey = maskingKeyGen.fold(Array.empty[Byte])(_())

      val commandPipeline: CPL = {
        case FrameCommand(frame) =>
          val frame1 = frame match {
            case DataFrame(fin, opcode, payload) =>
              state.pmce map (_.encode(payload)) match {
                case Some(x) => frame.copy(rsv1 = true, payload = x)
                case None    => frame
              }
            case _ => frame
          }
          commandPL(Tcp.Write(FrameRender.render(frame1, maskingKey)))
          if (frame1.opcode == Opcode.Close) {
            commandPL(Tcp.Close)
          }
        case FrameStreamCommand(frameStream) =>
          FrameRender.streamingRender(frameStream).foreach(f => commandPL(Tcp.Write(FrameRender.render(f, maskingKey))))
          frameStream.close
        case cmd => commandPL(cmd)
      }

      val eventPipeline = eventPL
    }

  }
}
