' Starts StockBuddy without a black console window.
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(WScript.ScriptFullName)
sh.CurrentDirectory = dir
args = ""
For Each a In WScript.Arguments
  args = args & " " & a
Next
javaw = "javaw"
If fso.FileExists(dir & "\java-path.txt") Then
  p = Trim(fso.OpenTextFile(dir & "\java-path.txt").ReadLine)
  If fso.FileExists(p) Then javaw = p
End If
On Error Resume Next
sh.Run """" & javaw & """ -jar """ & dir & "\StockBuddy.jar""" & args, 0, False
If Err.Number <> 0 Then MsgBox "StockBuddy needs Java 17 or newer. Please run 'Install StockBuddy' again, or install Java from https://adoptium.net", 48, "StockBuddy"
