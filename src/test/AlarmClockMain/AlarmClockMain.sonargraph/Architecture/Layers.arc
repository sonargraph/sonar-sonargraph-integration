artifact Foundation
{

}

artifact Model
{
    //Specify an include pattern
    include "**/com/h2m/**/model/**"
}

artifact Presentation
{
    include "**/com/h2m/**/presentation/**"
    connect to Model
}

