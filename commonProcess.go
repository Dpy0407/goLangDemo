package main

import (
	"fmt"
)

func onMsgReceived(ctx IContex, msg IMessage) {
	switch msg.msgType {
	case MSG_POST_DATA:
		onDataReceived(ctx, msg)
		break
	case MSG_DATA_CONTINUE:
		onDataContinue(ctx, msg)
		break
	case MSG_DATA_ERROR:
		onDataError(ctx, msg)
		break
	case MSG_DATA_ACK_DONE:
		onDataAckDone(ctx, msg)
		break
	default:
		break
	}
}

func onDataReceived(ctx IContex, msg IMessage) {
	var resp IMessage
	var ret byte

	if DIVICE == msg.msgSrc {
		ret = ctx.device2mobileData.onRevice(msg.payload)
	} else if MOBILE == msg.msgSrc {
		ret = ctx.mobile2deviceData.onRevice(msg.payload)
	}

	resp.msgSrc = SERVER
	resp.msgId = msg.msgId
	resp.msgType = ret
	resp.payload = nil
	resp.msgDst = msg.msgSrc

	ctx.sendMessage(msg)
}

func onDataContinue(ctx IContex, msg IMessage) {
	var data []byte
	if DIVICE == msg.msgSrc {
		data = ctx.mobile2deviceData.getSendData()
	} else if MOBILE == msg.msgSrc {
		data = ctx.device2mobileData.getSendData()
	}

	if data == nil {
		fmt.Printf("data is empty, request from 0x%X\r\n", msg.msgSrc)
		return
	}

	dataSend(ctx, msg.msgSrc, data)
}

func onDataError(ctx IContex, msg IMessage) {
	var blockData IBlockData
	if DIVICE == msg.msgSrc {
		blockData = ctx.mobile2deviceData
	} else if MOBILE == msg.msgSrc {
		blockData = ctx.device2mobileData
	}

	// try send again
	if DATA_STATE_SENDING == blockData.state {
		data := blockData.getLastSendData()
		dataSend(ctx, msg.msgSrc, data)
	}

}

func onDataAckDone(ctx IContex, msg IMessage) {
	var blockData IBlockData
	if DIVICE == msg.msgSrc {
		blockData = ctx.mobile2deviceData
	} else if MOBILE == msg.msgSrc {
		blockData = ctx.device2mobileData
	}

	// init block data, prepare for next receive & send
	blockData.init()
}

func dataSend(ctx IContex, dst byte, data []byte) {
	if data == nil {
		fmt.Printf("data is empty\r\n")
		return
	}

	if dst != DIVICE && dst != MOBILE {
		fmt.Printf("invalid dst, dst = 0x%X", dst)
		return
	}

	var resp IMessage
	resp.msgSrc = SERVER
	resp.msgDst = dst
	resp.msgType = MSG_PUT_DATA
	ctx.msgIdMutex.Lock()
	resp.msgId = ctx.msgId
	ctx.msgId += 1
	ctx.msgIdMutex.Unlock()

	resp.payload = data
	ctx.sendMessage(resp)
}
