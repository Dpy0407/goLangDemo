package main

import (
	. "../blockData"
	. "../message"
	"log"
	"os"
	"time"
)

func mobileProcessLoop(ctx *IContex) {
	ctx.mobile2deviceData.Init()
	ctx.mobile2deviceData.SendStep = DATA_DEVICE_SEND_STEP
	for true {
		select {
		case msg := <-main2mobileChan:
			onMsgReceived(ctx, *msg)

		default:
			mobileStep(ctx)
		}
		time.Sleep(1 * time.Microsecond)
	}
}

func mobileStep(ctx *IContex) {
	baseStep(ctx, MOBILE)

	if ctx.fileTransMode {
		if ctx.fileInfo.State == DATA_STATE_DONE {
			log.Printf("file receive success!\r\n")
			ctx.fileInfo.File.Close()
			os.Rename(FILE_TMP_PATH, ctx.fileInfo.FilePath)
			ctx.fileInfo.Init()
		} else if DATA_STATE_RICIEVING == ctx.fileInfo.State {
			if time.Now().Unix()-ctx.fileInfo.RecieveTime > 10 {
				log.Printf("file receive failed...\r\n")
				ctx.fileInfo.File.Close()
				ctx.fileInfo.Init()
			}
		}

	}
}
